package com.heness.project.account.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountAuthenticationServiceTests {

	private static final Instant NOW = Instant.parse("2026-07-27T08:00:00Z");
	private static final AccountRecord ACCOUNT = new AccountRecord(
			1L,
			UUID.fromString("78cd6aa7-7bc0-4d71-b1ab-e26f4fdf570f"),
			"China_2026",
			"china_2026",
			"encoded",
			NOW.minusSeconds(60)
	);

	@Test
	void 注册原子创建账号和三十分钟七天会话() {
		FakeRepository repository = new FakeRepository();
		AccountAuthenticationService service = service(repository, new FakeFailureStore(), NOW);

		IssuedAuthentication result = service.register("China_2026", "Password123", "Password123", true);

		assertThat(repository.createdAccount.normalizedAccountName()).isEqualTo("china_2026");
		assertThat(repository.sessionCreated).isTrue();
		assertThat(result.accessExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
		assertThat(result.absoluteExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
		assertThat(result.toString()).doesNotContain("raw-access", "raw-refresh", "Password123");
	}

	@Test
	void 错误密码记录失败且返回统一原因() {
		FakeRepository repository = new FakeRepository();
		repository.foundAccount = ACCOUNT;
		FakeFailureStore failures = new FakeFailureStore();
		AccountAuthenticationService service = service(repository, failures, NOW);

		assertThatThrownBy(() -> service.login("China_2026", "wrong-password1", "127.0.0.1"))
				.isInstanceOfSatisfying(AuthFailure.class,
						failure -> assertThat(failure.reason()).isEqualTo(AuthFailureReason.INVALID_CREDENTIALS));
		assertThat(failures.recorded).isTrue();
		assertThat(repository.sessionCreated).isFalse();
	}

	@Test
	void 第五次失败立即返回剩余限制时间() {
		FakeRepository repository = new FakeRepository();
		FakeFailureStore failures = new FakeFailureStore();
		failures.blockAfterRecord = Duration.ofMinutes(15);
		AccountAuthenticationService service = service(repository, failures, NOW);

		assertThatThrownBy(() -> service.login("China_2026", "wrong-password1", "127.0.0.1"))
				.isInstanceOfSatisfying(AuthFailure.class, failure -> {
					assertThat(failure.reason()).isEqualTo(AuthFailureReason.RATE_LIMITED);
					assertThat(failure.retryAfter()).isEqualTo(Duration.ofMinutes(15));
				});
	}

	@Test
	void 长Token正常刷新并保持原绝对期限() {
		FakeRepository repository = new FakeRepository();
		repository.foundAccount = ACCOUNT;
		repository.refresh = new RefreshTokenRecord(
				7L, 9L, RefreshTokenStatus.ACTIVE, null, NOW.plus(Duration.ofDays(6)), NOW.plus(Duration.ofDays(6)));
		AccountAuthenticationService service = service(repository, new FakeFailureStore(), NOW);

		IssuedAuthentication refreshed = service.refresh("refresh-cookie");

		assertThat(repository.rotated).isTrue();
		assertThat(refreshed.absoluteExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(6)));
	}

	@Test
	void 五秒内重复刷新返回冲突且不撤销会话() {
		FakeRepository repository = new FakeRepository();
		repository.refresh = new RefreshTokenRecord(
				7L, 9L, RefreshTokenStatus.ROTATED, NOW.minusSeconds(5), NOW.plusSeconds(30), NOW.plusSeconds(30));
		AccountAuthenticationService service = service(repository, new FakeFailureStore(), NOW);

		assertThatThrownBy(() -> service.refresh("refresh-cookie"))
				.isInstanceOfSatisfying(AuthFailure.class,
						failure -> assertThat(failure.reason()).isEqualTo(AuthFailureReason.REFRESH_CONFLICT));
		assertThat(repository.revoked).isFalse();
	}

	@Test
	void 超过五秒重放撤销会话() {
		FakeRepository repository = new FakeRepository();
		repository.refresh = new RefreshTokenRecord(
				7L, 9L, RefreshTokenStatus.ROTATED, NOW.minusSeconds(6), NOW.plusSeconds(30), NOW.plusSeconds(30));
		AccountAuthenticationService service = service(repository, new FakeFailureStore(), NOW);

		assertThatThrownBy(() -> service.refresh("refresh-cookie"))
				.isInstanceOfSatisfying(AuthFailure.class,
						failure -> assertThat(failure.reason()).isEqualTo(AuthFailureReason.REFRESH_TOKEN_INVALID));
		assertThat(repository.revoked).isTrue();
	}

	private AccountAuthenticationService service(
			FakeRepository repository,
			FakeFailureStore failures,
			Instant now) {
		return new AccountAuthenticationService(
				repository,
				failures,
				new PasswordService() {
					@Override
					public String encode(String rawPassword) { return "encoded"; }
					@Override
					public boolean matchesOrDummy(String rawPassword, String encodedPassword) {
						return "Password123".equals(rawPassword) && encodedPassword != null;
					}
				},
				new FakeTokenService(),
				new FailureKeyService() {
					@Override public String accountKey(String value) { return "account-key"; }
					@Override public String ipKey(String value) { return "ip-key"; }
				},
				Clock.fixed(now, ZoneOffset.UTC)
		);
	}

	private static final class FakeTokenService implements TokenService {
		private final Queue<String> names = new ArrayDeque<>(java.util.List.of("access", "refresh"));
		@Override public SecretToken issue() {
			String name = names.remove();
			return new SecretToken("raw-" + name, "digest-" + name);
		}
		@Override public String digest(String rawToken) { return "digest-" + rawToken; }
	}

	private static final class FakeFailureStore implements LoginFailureStore {
		boolean recorded;
		Duration blockAfterRecord;
		@Override public Optional<Duration> blockedFor(String accountKey, String ipKey, Instant now) {
			return Optional.empty();
		}
		@Override public Optional<Duration> recordFailure(String accountKey, String ipKey, Instant now) {
			recorded = true;
			return Optional.ofNullable(blockAfterRecord);
		}
		@Override public void clearAccountFailures(String accountKey) { }
	}

	private static final class FakeRepository implements AuthenticationRepository {
		AccountRecord createdAccount;
		AccountRecord foundAccount;
		RefreshTokenRecord refresh;
		boolean sessionCreated;
		boolean rotated;
		boolean revoked;
		String activeAccessHash;

		@Override
		public AccountRecord createAccount(UUID publicId, String accountName, String normalized, String hash, Instant createdAt) {
			createdAccount = new AccountRecord(1L, publicId, accountName, normalized, hash, createdAt);
			foundAccount = createdAccount;
			return createdAccount;
		}
		@Override public Optional<AccountRecord> findAccountByNormalizedName(String value) {
			return Optional.ofNullable(foundAccount);
		}
		@Override
		public void createSession(AccountRecord account, UUID id, String accessHash, String refreshHash,
				Instant accessExpiresAt, Instant absoluteExpiresAt, Instant createdAt) {
			sessionCreated = true;
			activeAccessHash = accessHash;
		}
		@Override public Optional<AccountView> findAccountByActiveAccessToken(String hash, Instant now) {
			return hash.equals(activeAccessHash) && foundAccount != null
					? Optional.of(foundAccount.toView()) : Optional.empty();
		}
		@Override public Optional<RefreshTokenRecord> lockRefreshToken(String hash) { return Optional.ofNullable(refresh); }
		@Override
		public void rotateSession(RefreshTokenRecord current, String accessHash, String refreshHash,
				Instant accessExpiresAt, Instant rotatedAt) {
			rotated = true;
			activeAccessHash = accessHash;
		}
		@Override public void revokeSession(long sessionId, Instant revokedAt) { revoked = true; }
		@Override public void revokeByTokenDigests(String accessHash, String refreshHash, Instant revokedAt) { revoked = true; }
	}
}
