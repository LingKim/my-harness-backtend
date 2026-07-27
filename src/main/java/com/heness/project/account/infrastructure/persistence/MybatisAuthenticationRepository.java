package com.heness.project.account.infrastructure.persistence;

import com.heness.project.account.application.AccountRecord;
import com.heness.project.account.application.AccountView;
import com.heness.project.account.application.AuthenticationRepository;
import com.heness.project.account.application.DuplicateAccount;
import com.heness.project.account.application.RefreshTokenRecord;
import com.heness.project.account.application.RefreshTokenStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
class MybatisAuthenticationRepository implements AuthenticationRepository {

	private final AccountMapper accountMapper;
	private final AuthSessionMapper sessionMapper;
	private final RefreshTokenMapper refreshTokenMapper;

	MybatisAuthenticationRepository(
			AccountMapper accountMapper,
			AuthSessionMapper sessionMapper,
			RefreshTokenMapper refreshTokenMapper) {
		this.accountMapper = accountMapper;
		this.sessionMapper = sessionMapper;
		this.refreshTokenMapper = refreshTokenMapper;
	}

	@Override
	public AccountRecord createAccount(
			UUID publicId,
			String accountName,
			String normalizedAccountName,
			String passwordHash,
			Instant createdAt) {
		AccountRow row = new AccountRow();
		row.setPublicId(publicId.toString());
		row.setAccountName(accountName);
		row.setNormalizedAccountName(normalizedAccountName);
		row.setPasswordHash(passwordHash);
		row.setCreatedAt(DatabaseTimes.toDatabase(createdAt));
		try {
			accountMapper.insert(row);
		} catch (DuplicateKeyException duplicate) {
			throw new DuplicateAccount();
		}
		return toRecord(row);
	}

	@Override
	public Optional<AccountRecord> findAccountByNormalizedName(String normalizedAccountName) {
		return Optional.ofNullable(accountMapper.findByNormalizedName(normalizedAccountName))
				.map(this::toRecord);
	}

	@Override
	public void createSession(
			AccountRecord account,
			UUID publicSessionId,
			String accessTokenHash,
			String refreshTokenHash,
			Instant accessExpiresAt,
			Instant absoluteExpiresAt,
			Instant createdAt) {
		AuthSessionRow session = new AuthSessionRow();
		session.setPublicId(publicSessionId.toString());
		session.setAccountId(account.internalId());
		session.setAccessTokenHash(accessTokenHash);
		session.setAccessExpiresAt(DatabaseTimes.toDatabase(accessExpiresAt));
		session.setAbsoluteExpiresAt(DatabaseTimes.toDatabase(absoluteExpiresAt));
		session.setStatus("ACTIVE");
		session.setCreatedAt(DatabaseTimes.toDatabase(createdAt));
		session.setUpdatedAt(DatabaseTimes.toDatabase(createdAt));
		sessionMapper.insert(session);

		RefreshTokenRow refresh = new RefreshTokenRow();
		refresh.setSessionId(session.getId());
		refresh.setTokenHash(refreshTokenHash);
		refresh.setStatus("ACTIVE");
		refresh.setCreatedAt(DatabaseTimes.toDatabase(createdAt));
		refresh.setExpiresAt(DatabaseTimes.toDatabase(absoluteExpiresAt));
		refreshTokenMapper.insert(refresh);
	}

	@Override
	public Optional<AccountView> findAccountByActiveAccessToken(String accessTokenHash, Instant now) {
		return Optional.ofNullable(sessionMapper.findAccountByActiveAccess(
				accessTokenHash,
				DatabaseTimes.toDatabase(now)
		)).map(row -> new AccountView(
				UUID.fromString(row.publicId()),
				row.accountName(),
				DatabaseTimes.toInstant(row.createdAt())
		));
	}

	@Override
	public Optional<RefreshTokenRecord> lockRefreshToken(String refreshTokenHash) {
		return Optional.ofNullable(refreshTokenMapper.lockByHash(refreshTokenHash))
				.map(row -> new RefreshTokenRecord(
						row.tokenId(),
						row.sessionId(),
						RefreshTokenStatus.valueOf(row.status()),
						DatabaseTimes.toInstant(row.rotatedAt()),
						DatabaseTimes.toInstant(row.expiresAt()),
						DatabaseTimes.toInstant(row.absoluteExpiresAt())
				));
	}

	@Override
	public void rotateSession(
			RefreshTokenRecord current,
			String nextAccessTokenHash,
			String nextRefreshTokenHash,
			Instant nextAccessExpiresAt,
			Instant rotatedAt) {
		if (refreshTokenMapper.markRotated(current.tokenId(), DatabaseTimes.toDatabase(rotatedAt)) != 1) {
			throw new IllegalStateException("长 Token 状态已变化");
		}
		if (sessionMapper.rotateAccess(
				current.sessionId(),
				nextAccessTokenHash,
				DatabaseTimes.toDatabase(nextAccessExpiresAt),
				DatabaseTimes.toDatabase(rotatedAt)) != 1) {
			throw new IllegalStateException("会话无法轮换");
		}
		RefreshTokenRow next = new RefreshTokenRow();
		next.setSessionId(current.sessionId());
		next.setTokenHash(nextRefreshTokenHash);
		next.setStatus("ACTIVE");
		next.setCreatedAt(DatabaseTimes.toDatabase(rotatedAt));
		next.setExpiresAt(DatabaseTimes.toDatabase(current.absoluteExpiresAt()));
		refreshTokenMapper.insert(next);
	}

	@Override
	public void revokeSession(long sessionId, Instant revokedAt) {
		sessionMapper.revoke(sessionId, DatabaseTimes.toDatabase(revokedAt));
		refreshTokenMapper.revokeActiveForSession(sessionId);
	}

	@Override
	public void revokeByTokenDigests(String accessTokenHash, String refreshTokenHash, Instant revokedAt) {
		Long sessionId = accessTokenHash == null ? null : sessionMapper.findSessionIdByAccessHash(accessTokenHash);
		if (sessionId == null && refreshTokenHash != null) {
			sessionId = sessionMapper.findSessionIdByRefreshHash(refreshTokenHash);
		}
		if (sessionId != null) {
			revokeSession(sessionId, revokedAt);
		}
	}

	private AccountRecord toRecord(AccountRow row) {
		return new AccountRecord(
				row.getId(),
				UUID.fromString(row.getPublicId()),
				row.getAccountName(),
				row.getNormalizedAccountName(),
				row.getPasswordHash(),
				DatabaseTimes.toInstant(row.getCreatedAt())
		);
	}
}
