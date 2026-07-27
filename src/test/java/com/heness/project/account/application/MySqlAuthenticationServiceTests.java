package com.heness.project.account.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
@Import(MySqlAuthenticationServiceTests.MutableClockConfiguration.class)
@EnabledIfEnvironmentVariable(named = "DATABASE_ENABLED", matches = "true")
class MySqlAuthenticationServiceTests {

	private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");
	private static final String PASSWORD = "Travel2026!";

	@Autowired
	private AccountAuthenticationService service;

	@Autowired
	private FailureKeyService failureKeyService;

	@Autowired
	private MutableClock clock;

	@Autowired
	private DataSource dataSource;

	@BeforeEach
	void resetClock() {
		clock.set(NOW);
	}

	@Test
	void failedLoginTransactionsCommitThresholdAndResetAfterFifteenMinutes() throws Exception {
		String accountName = uniqueAccountName();
		String normalizedName = accountName.toLowerCase();
		String clientIp = "203.0.113.41";
		String accountKey = failureKeyService.accountKey(normalizedName);
		String ipKey = failureKeyService.ipKey(clientIp);

		try {
			service.register(accountName, PASSWORD, PASSWORD, true);
			for (int attempt = 1; attempt <= 4; attempt++) {
				assertFailure(
						() -> service.login(accountName, "Wrong2026!", clientIp),
						AuthFailureReason.INVALID_CREDENTIALS
				);
			}
			assertFailure(
					() -> service.login(accountName, "Wrong2026!", clientIp),
					AuthFailureReason.RATE_LIMITED
			);
			assertFailure(
					() -> service.login(accountName, "Wrong2026!", clientIp),
					AuthFailureReason.RATE_LIMITED
			);
			assertThat(failureCount("ACCOUNT", accountKey)).isEqualTo(5);

			clock.set(NOW.plus(Duration.ofMinutes(15)));
			assertFailure(
					() -> service.login(accountName, "Wrong2026!", clientIp),
					AuthFailureReason.INVALID_CREDENTIALS
			);
			assertThat(failureCount("ACCOUNT", accountKey)).isEqualTo(1);
		} finally {
			cleanup(normalizedName, accountKey, ipKey);
		}
	}

	@Test
	void shortTokenExpiresAtThirtyMinutesAndRefreshKeepsSevenDayDeadline() throws Exception {
		String accountName = uniqueAccountName();
		String normalizedName = accountName.toLowerCase();
		String accountKey = failureKeyService.accountKey(normalizedName);
		String ipKey = failureKeyService.ipKey("203.0.113.42");

		try {
			IssuedAuthentication registered = service.register(accountName, PASSWORD, PASSWORD, true);
			clock.set(NOW.plus(Duration.ofMinutes(30)));
			assertFailure(
					() -> service.currentAccount(registered.accessToken().rawValue()),
					AuthFailureReason.ACCESS_TOKEN_INVALID
			);

			IssuedAuthentication refreshed = service.refresh(registered.refreshToken().rawValue());
			assertThat(service.currentAccount(refreshed.accessToken().rawValue()).accountName())
					.isEqualTo(accountName);
			assertThat(refreshed.absoluteExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
		} finally {
			cleanup(normalizedName, accountKey, ipKey);
		}
	}

	@Test
	void refreshConflictKeepsSessionButReplayAfterGraceCommitsRevocation() throws Exception {
		String accountName = uniqueAccountName();
		String normalizedName = accountName.toLowerCase();
		String accountKey = failureKeyService.accountKey(normalizedName);
		String ipKey = failureKeyService.ipKey("203.0.113.43");

		try {
			IssuedAuthentication registered = service.register(accountName, PASSWORD, PASSWORD, true);
			IssuedAuthentication refreshed = service.refresh(registered.refreshToken().rawValue());

			clock.set(NOW.plus(Duration.ofSeconds(4)));
			assertFailure(
					() -> service.refresh(registered.refreshToken().rawValue()),
					AuthFailureReason.REFRESH_CONFLICT
			);
			assertThat(service.currentAccount(refreshed.accessToken().rawValue()).accountName())
					.isEqualTo(accountName);

			clock.set(NOW.plus(Duration.ofSeconds(6)));
			assertFailure(
					() -> service.refresh(registered.refreshToken().rawValue()),
					AuthFailureReason.REFRESH_TOKEN_INVALID
			);
			assertFailure(
					() -> service.currentAccount(refreshed.accessToken().rawValue()),
					AuthFailureReason.ACCESS_TOKEN_INVALID
			);
		} finally {
			cleanup(normalizedName, accountKey, ipKey);
		}
	}

	private static void assertFailure(ThrowingCall call, AuthFailureReason reason) {
		Throwable thrown = catchThrowable(call::run);
		assertThat(thrown).isInstanceOf(AuthFailure.class);
		AuthFailure failure = (AuthFailure) thrown;
		assertThat(failure.reason()).isEqualTo(reason);
	}

	private int failureCount(String keyType, String keyHash) throws SQLException {
		try (Connection connection = dataSource.getConnection();
			 PreparedStatement statement = connection.prepareStatement("""
					 SELECT failure_count
					 FROM account_auth_failure_bucket
					 WHERE key_type = ? AND key_hash = ?
					 """)) {
			statement.setString(1, keyType);
			statement.setString(2, keyHash);
			try (var rows = statement.executeQuery()) {
				return rows.next() ? rows.getInt(1) : 0;
			}
		}
	}

	private void cleanup(String normalizedName, String accountKey, String ipKey) throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try {
				execute(connection, """
						DELETE r
						FROM account_refresh_token r
						JOIN account_auth_session s ON s.id = r.session_id
						JOIN account a ON a.id = s.account_id
						WHERE a.normalized_account_name = ?
						""", normalizedName);
				execute(connection, """
						DELETE s
						FROM account_auth_session s
						JOIN account a ON a.id = s.account_id
						WHERE a.normalized_account_name = ?
						""", normalizedName);
				execute(connection, "DELETE FROM account WHERE normalized_account_name = ?", normalizedName);
				execute(connection,
						"DELETE FROM account_auth_failure_bucket WHERE key_hash IN (?, ?)",
						accountKey,
						ipKey);
				connection.commit();
			} catch (SQLException failure) {
				connection.rollback();
				throw failure;
			}
		}
	}

	private static void execute(Connection connection, String sql, String... values) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int index = 0; index < values.length; index++) {
				statement.setString(index + 1, values[index]);
			}
			statement.executeUpdate();
		}
	}

	private static String uniqueAccountName() {
		return "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
	}

	@FunctionalInterface
	private interface ThrowingCall {
		Object run();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class MutableClockConfiguration {
		@Bean
		@Primary
		MutableClock mutableAccountClock() {
			return new MutableClock(NOW);
		}
	}

	static final class MutableClock extends Clock {
		private final AtomicReference<Instant> current;

		MutableClock(Instant initial) {
			current = new AtomicReference<>(initial);
		}

		void set(Instant instant) {
			current.set(instant);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return current.get();
		}
	}
}
