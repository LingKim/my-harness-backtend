package com.heness.project.account.application;

import com.heness.project.account.domain.AccountName;
import com.heness.project.account.domain.AccountRuleViolation;
import com.heness.project.account.domain.AuthSessionWindow;
import com.heness.project.account.domain.PasswordPolicy;
import com.heness.project.account.domain.RefreshTokenUse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class AccountAuthenticationService {

	private final AuthenticationRepository repository;
	private final LoginFailureStore loginFailureStore;
	private final PasswordService passwordService;
	private final TokenService tokenService;
	private final FailureKeyService failureKeyService;
	private final Clock clock;

	public AccountAuthenticationService(
			AuthenticationRepository repository,
			LoginFailureStore loginFailureStore,
			PasswordService passwordService,
			TokenService tokenService,
			FailureKeyService failureKeyService,
			Clock clock) {
		this.repository = repository;
		this.loginFailureStore = loginFailureStore;
		this.passwordService = passwordService;
		this.tokenService = tokenService;
		this.failureKeyService = failureKeyService;
		this.clock = clock;
	}

	@Transactional
	public IssuedAuthentication register(
			String accountNameValue,
			String password,
			String confirmPassword,
			boolean termsAccepted) {
		AccountName accountName;
		try {
			accountName = AccountName.of(accountNameValue);
			PasswordPolicy.requireValid(password);
		} catch (AccountRuleViolation violation) {
			throw AuthFailure.of(AuthFailureReason.REGISTRATION_REJECTED);
		}
		if (!termsAccepted || !password.equals(confirmPassword)) {
			throw AuthFailure.of(AuthFailureReason.REGISTRATION_REJECTED);
		}

		Instant now = clock.instant();
		AccountRecord account;
		try {
			account = repository.createAccount(
					UUID.randomUUID(),
					accountName.displayValue(),
					accountName.normalizedValue(),
					passwordService.encode(password),
					now
			);
		} catch (DuplicateAccount duplicate) {
			throw AuthFailure.of(AuthFailureReason.REGISTRATION_REJECTED);
		}
		return createSession(account, now);
	}

	@Transactional(noRollbackFor = AuthFailure.class)
	public IssuedAuthentication login(String accountNameValue, String password, String clientIp) {
		AccountName accountName;
		try {
			accountName = AccountName.of(accountNameValue);
		} catch (AccountRuleViolation violation) {
			throw AuthFailure.of(AuthFailureReason.INVALID_CREDENTIALS);
		}
		Instant now = clock.instant();
		String accountKey = failureKeyService.accountKey(accountName.normalizedValue());
		String ipKey = failureKeyService.ipKey(clientIp);
		throwIfBlocked(loginFailureStore.blockedFor(accountKey, ipKey, now));

		Optional<AccountRecord> foundAccount = repository.findAccountByNormalizedName(accountName.normalizedValue());
		String storedHash = foundAccount.map(AccountRecord::passwordHash).orElse(null);
		if (!passwordService.matchesOrDummy(password, storedHash)) {
			Optional<Duration> blockedFor = loginFailureStore.recordFailure(accountKey, ipKey, now);
			throwIfBlocked(blockedFor);
			throw AuthFailure.of(AuthFailureReason.INVALID_CREDENTIALS);
		}

		AccountRecord account = foundAccount.orElseThrow(
				() -> AuthFailure.of(AuthFailureReason.INVALID_CREDENTIALS));
		loginFailureStore.clearAccountFailures(accountKey);
		return createSession(account, now);
	}

	@Transactional(noRollbackFor = AuthFailure.class)
	public IssuedAuthentication refresh(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			throw AuthFailure.of(AuthFailureReason.REFRESH_TOKEN_INVALID);
		}
		Instant now = clock.instant();
		RefreshTokenRecord current = repository.lockRefreshToken(tokenService.digest(rawRefreshToken))
				.orElseThrow(() -> AuthFailure.of(AuthFailureReason.REFRESH_TOKEN_INVALID));
		if (current.status() == RefreshTokenStatus.ROTATED) {
			RefreshTokenUse use = RefreshTokenUse.classifyRotated(current.rotatedAt(), now);
			if (use == RefreshTokenUse.CONCURRENT_CONFLICT) {
				throw AuthFailure.of(AuthFailureReason.REFRESH_CONFLICT);
			}
			repository.revokeSession(current.sessionId(), now);
			throw AuthFailure.of(AuthFailureReason.REFRESH_TOKEN_INVALID);
		}
		if (current.status() != RefreshTokenStatus.ACTIVE
				|| !now.isBefore(current.expiresAt())
				|| !now.isBefore(current.absoluteExpiresAt())) {
			repository.revokeSession(current.sessionId(), now);
			throw AuthFailure.of(AuthFailureReason.REFRESH_TOKEN_INVALID);
		}

		SecretToken accessToken = tokenService.issue();
		SecretToken refreshToken = tokenService.issue();
		Instant accessExpiresAt = now.plus(Duration.ofMinutes(30));
		if (accessExpiresAt.isAfter(current.absoluteExpiresAt())) {
			accessExpiresAt = current.absoluteExpiresAt();
		}
		repository.rotateSession(
				current,
				accessToken.digest(),
				refreshToken.digest(),
				accessExpiresAt,
				now
		);
		AccountView account = repository.findAccountByActiveAccessToken(accessToken.digest(), now)
				.orElseThrow(() -> new IllegalStateException("轮换后会话必须可读取"));
		return new IssuedAuthentication(
				account,
				accessToken,
				refreshToken,
				accessExpiresAt,
				current.absoluteExpiresAt()
		);
	}

	@Transactional
	public void logout(String rawAccessToken, String rawRefreshToken) {
		String accessDigest = digestNullable(rawAccessToken);
		String refreshDigest = digestNullable(rawRefreshToken);
		repository.revokeByTokenDigests(accessDigest, refreshDigest, clock.instant());
	}

	@Transactional(readOnly = true)
	public AccountView currentAccount(String rawAccessToken) {
		if (rawAccessToken == null || rawAccessToken.isBlank()) {
			throw AuthFailure.of(AuthFailureReason.ACCESS_TOKEN_INVALID);
		}
		return repository.findAccountByActiveAccessToken(tokenService.digest(rawAccessToken), clock.instant())
				.orElseThrow(() -> AuthFailure.of(AuthFailureReason.ACCESS_TOKEN_INVALID));
	}

	private IssuedAuthentication createSession(AccountRecord account, Instant now) {
		SecretToken accessToken = tokenService.issue();
		SecretToken refreshToken = tokenService.issue();
		AuthSessionWindow window = AuthSessionWindow.start(now);
		repository.createSession(
				account,
				UUID.randomUUID(),
				accessToken.digest(),
				refreshToken.digest(),
				window.accessExpiresAt(),
				window.absoluteExpiresAt(),
				now
		);
		return new IssuedAuthentication(
				account.toView(),
				accessToken,
				refreshToken,
				window.accessExpiresAt(),
				window.absoluteExpiresAt()
		);
	}

	private void throwIfBlocked(Optional<Duration> blockedFor) {
		blockedFor.filter(duration -> !duration.isZero() && !duration.isNegative())
				.ifPresent(duration -> {
					throw AuthFailure.rateLimited(duration);
				});
	}

	private String digestNullable(String rawToken) {
		return rawToken == null || rawToken.isBlank() ? null : tokenService.digest(rawToken);
	}
}
