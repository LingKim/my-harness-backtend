package com.heness.project.account.infrastructure.persistence;

import com.heness.project.account.application.RefreshTokenRecord;
import com.heness.project.account.application.AccountAuthenticationService;
import com.heness.project.account.application.IssuedAuthentication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DATABASE_ENABLED", matches = "true")
class MySqlAuthenticationPersistenceTests {

	private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

	@Autowired
	private AccountMapper accountMapper;

	@Autowired
	private AuthSessionMapper sessionMapper;

	@Autowired
	private RefreshTokenMapper refreshTokenMapper;

	@Autowired
	private LoginFailureMapper loginFailureMapper;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private Flyway flyway;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private AccountAuthenticationService authentication;

	@Autowired
	private AccountPreferencesMapper preferencesMapper;

	@Autowired
	private TravelContextMapper travelContextMapper;

	@Autowired
	private MybatisAuthenticationRepository authenticationRepository;

	@Test
	void flywayKeepsAuthenticationTablesAtCurrentVersion() throws Exception {
		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");

		Set<String> tables = new HashSet<>();
		try (var connection = dataSource.getConnection()) {
			DatabaseMetaData metadata = connection.getMetaData();
			try (ResultSet rows = metadata.getTables(connection.getCatalog(), null, "account%", new String[]{"TABLE"})) {
				while (rows.next()) {
					tables.add(rows.getString("TABLE_NAME"));
				}
			}
		}

		assertThat(tables).contains(
				"account",
				"account_auth_session",
				"account_refresh_token",
				"account_auth_failure_bucket"
		);
	}

	@Test
	@Transactional
	void normalizedAccountNameIsUniqueRegardlessOfDisplayCase() {
		String normalizedName = uniqueAccountName().toLowerCase();
		accountMapper.insert(account("China_Travel", normalizedName));

		assertThatThrownBy(() -> accountMapper.insert(account("china_travel", normalizedName)))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	@Transactional
	void registrationTransactionCreatesDefaultPreferencesAndEmptyTravelContext() {
		String accountName = uniqueAccountName();
		IssuedAuthentication issued = authentication.register(
				accountName, "Travel2026!", "Travel2026!", true);

		AccountAccessProjection preferences = preferencesMapper.findAccountView(
				issued.account().accountId().toString());
		TravelContextRow context = travelContextMapper.findByPublicAccountId(
				issued.account().accountId().toString());
		assertThat(preferences.preferredLanguage()).isEqualTo("zh-CN");
		assertThat(context.getVersion()).isZero();
		assertThat(context.getCountryOrRegion()).isNull();
		assertThat(context.getCity()).isNull();
		assertThat(context.getTripStartDate()).isNull();
		assertThat(context.getTripEndDate()).isNull();
		assertThat(context.getAssistanceNeeds()).isNull();
	}

	@Test
	void registrationInitializationFailureRollsBackAccountAndDefaultRowsTogether() {
		String accountName = uniqueAccountName();
		String normalizedName = accountName.toLowerCase();
		UUID publicId = UUID.randomUUID();
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);

		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
			var account = authenticationRepository.createAccount(publicId, accountName, normalizedName,
					"$2a$12$integration-fixture-password-hash-placeholder-value", NOW);
			authenticationRepository.initializeAccountPreferences(account.internalId(), NOW);
			authenticationRepository.initializeAccountPreferences(account.internalId(), NOW);
		})).isInstanceOf(DuplicateKeyException.class);

		assertThat(accountMapper.findByNormalizedName(normalizedName)).isNull();
		assertThat(preferencesMapper.findAccountView(publicId.toString())).isNull();
		assertThat(travelContextMapper.findByPublicAccountId(publicId.toString())).isNull();
	}

	@Test
	@Transactional
	void accessTokenDigestIsUnique() {
		AccountRow account = account(uniqueAccountName(), uniqueAccountName().toLowerCase());
		accountMapper.insert(account);
		String digest = digest();
		sessionMapper.insert(session(account.getId(), digest));

		assertThatThrownBy(() -> sessionMapper.insert(session(account.getId(), digest)))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	@Transactional
	void refreshTokenDigestIsUnique() {
		AccountRow account = account(uniqueAccountName(), uniqueAccountName().toLowerCase());
		accountMapper.insert(account);
		AuthSessionRow firstSession = session(account.getId(), digest());
		AuthSessionRow secondSession = session(account.getId(), digest());
		sessionMapper.insert(firstSession);
		sessionMapper.insert(secondSession);
		String digest = digest();
		refreshTokenMapper.insert(refresh(firstSession.getId(), digest));

		assertThatThrownBy(() -> refreshTokenMapper.insert(refresh(secondSession.getId(), digest)))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	@Transactional
	void accountFailureThresholdCoversFourFiveAndSixAttempts() {
		MybatisLoginFailureStore store = new MybatisLoginFailureStore(loginFailureMapper);
		String accountKey = digest();
		String ipKey = digest();

		for (int attempt = 1; attempt <= 4; attempt++) {
			assertThat(store.recordFailure(accountKey, ipKey, NOW)).isEmpty();
		}
		assertThat(store.recordFailure(accountKey, ipKey, NOW)).contains(Duration.ofMinutes(15));
		assertThat(store.recordFailure(accountKey, ipKey, NOW)).contains(Duration.ofMinutes(15));
		assertThat(loginFailureMapper.find("ACCOUNT", accountKey).failureCount()).isEqualTo(5);
	}

	@Test
	@Transactional
	void ipFailureThresholdCoversNineteenTwentyAndTwentyOneAttempts() {
		MybatisLoginFailureStore store = new MybatisLoginFailureStore(loginFailureMapper);
		String ipKey = digest();

		for (int attempt = 1; attempt <= 19; attempt++) {
			assertThat(store.recordFailure(digest(), ipKey, NOW)).isEmpty();
		}
		assertThat(store.recordFailure(digest(), ipKey, NOW)).contains(Duration.ofMinutes(15));
		assertThat(store.recordFailure(digest(), ipKey, NOW)).contains(Duration.ofMinutes(15));
		assertThat(loginFailureMapper.find("IP", ipKey).failureCount()).isEqualTo(20);
	}

	@Test
	@Transactional
	void expiredFailureWindowResetsToFirstAttempt() {
		MybatisLoginFailureStore store = new MybatisLoginFailureStore(loginFailureMapper);
		String accountKey = digest();
		String ipKey = digest();
		LocalDateTime oldTime = DatabaseTimes.toDatabase(NOW.minus(Duration.ofMinutes(16)));
		loginFailureMapper.ensureExists("ACCOUNT", accountKey, oldTime);
		LoginFailureRow row = loginFailureMapper.lock("ACCOUNT", accountKey);
		loginFailureMapper.update(row.id(), oldTime, 4, null, oldTime);

		assertThat(store.recordFailure(accountKey, ipKey, NOW)).isEmpty();
		LoginFailureRow reset = loginFailureMapper.find("ACCOUNT", accountKey);
		assertThat(reset.failureCount()).isEqualTo(1);
		assertThat(DatabaseTimes.toInstant(reset.windowStartedAt())).isEqualTo(NOW);
	}

	@Test
	void refreshTokenRowLockSerializesConcurrentTransactions() throws Exception {
		TransactionTemplate transactions = new TransactionTemplate(transactionManager);
		Fixture fixture = transactions.execute(status -> insertFixture());
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch firstLocked = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);

		try {
			Future<RefreshTokenProjection> first = executor.submit(() -> transactions.execute(status -> {
				RefreshTokenProjection locked = refreshTokenMapper.lockByHash(fixture.refreshDigest());
				firstLocked.countDown();
				await(releaseFirst);
				return locked;
			}));
			assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();

			Future<RefreshTokenProjection> second = executor.submit(() ->
					transactions.execute(status -> refreshTokenMapper.lockByHash(fixture.refreshDigest())));
			assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS))
					.isInstanceOf(TimeoutException.class);

			releaseFirst.countDown();
			assertThat(first.get(5, TimeUnit.SECONDS).tokenId()).isEqualTo(fixture.refreshTokenId());
			assertThat(second.get(5, TimeUnit.SECONDS).tokenId()).isEqualTo(fixture.refreshTokenId());
		} finally {
			releaseFirst.countDown();
			executor.shutdownNow();
			cleanup(fixture);
		}
	}

	@Test
	void refreshRotationRollsBackAllWritesWhenTransactionFails() {
		TransactionTemplate transactions = new TransactionTemplate(transactionManager);
		Fixture fixture = transactions.execute(status -> insertFixture());
		String nextAccessDigest = digest();
		String nextRefreshDigest = digest();
		MybatisAuthenticationRepository repository =
				new MybatisAuthenticationRepository(accountMapper, sessionMapper, refreshTokenMapper);

		try {
			assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
				RefreshTokenRecord current = repository.lockRefreshToken(fixture.refreshDigest()).orElseThrow();
				repository.rotateSession(
						current,
						nextAccessDigest,
						nextRefreshDigest,
						NOW.plus(Duration.ofMinutes(30)),
						NOW
				);
				throw new ExpectedRollback();
			})).isInstanceOf(ExpectedRollback.class);

			transactions.executeWithoutResult(status -> {
				RefreshTokenProjection original = refreshTokenMapper.lockByHash(fixture.refreshDigest());
				assertThat(original.status()).isEqualTo("ACTIVE");
				assertThat(refreshTokenMapper.lockByHash(nextRefreshDigest)).isNull();
				assertThat(sessionMapper.findAccountByActiveAccess(
						fixture.accessDigest(),
						DatabaseTimes.toDatabase(NOW))).isNotNull();
				assertThat(sessionMapper.findAccountByActiveAccess(
						nextAccessDigest,
						DatabaseTimes.toDatabase(NOW))).isNull();
			});
		} finally {
			cleanup(fixture);
		}
	}

	private Fixture insertFixture() {
		AccountRow account = account(uniqueAccountName(), uniqueAccountName().toLowerCase());
		accountMapper.insert(account);
		String accessDigest = digest();
		AuthSessionRow session = session(account.getId(), accessDigest);
		sessionMapper.insert(session);
		String refreshDigest = digest();
		RefreshTokenRow refresh = refresh(session.getId(), refreshDigest);
		refreshTokenMapper.insert(refresh);
		return new Fixture(account.getId(), session.getId(), refresh.getId(), accessDigest, refreshDigest);
	}

	private void cleanup(Fixture fixture) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			refreshTokenMapper.deleteById(fixture.refreshTokenId());
			sessionMapper.deleteById(fixture.sessionId());
			accountMapper.deleteById(fixture.accountId());
		});
	}

	private static AccountRow account(String displayName, String normalizedName) {
		AccountRow row = new AccountRow();
		row.setPublicId(UUID.randomUUID().toString());
		row.setAccountName(displayName);
		row.setNormalizedAccountName(normalizedName);
		row.setPasswordHash("$2a$12$integration-fixture-password-hash-placeholder-value");
		row.setCreatedAt(DatabaseTimes.toDatabase(NOW));
		return row;
	}

	private static AuthSessionRow session(long accountId, String accessDigest) {
		AuthSessionRow row = new AuthSessionRow();
		row.setPublicId(UUID.randomUUID().toString());
		row.setAccountId(accountId);
		row.setAccessTokenHash(accessDigest);
		row.setAccessExpiresAt(DatabaseTimes.toDatabase(NOW.plus(Duration.ofMinutes(30))));
		row.setAbsoluteExpiresAt(DatabaseTimes.toDatabase(NOW.plus(Duration.ofDays(7))));
		row.setStatus("ACTIVE");
		row.setCreatedAt(DatabaseTimes.toDatabase(NOW));
		row.setUpdatedAt(DatabaseTimes.toDatabase(NOW));
		return row;
	}

	private static RefreshTokenRow refresh(long sessionId, String refreshDigest) {
		RefreshTokenRow row = new RefreshTokenRow();
		row.setSessionId(sessionId);
		row.setTokenHash(refreshDigest);
		row.setStatus("ACTIVE");
		row.setCreatedAt(DatabaseTimes.toDatabase(NOW));
		row.setExpiresAt(DatabaseTimes.toDatabase(NOW.plus(Duration.ofDays(7))));
		return row;
	}

	private static String uniqueAccountName() {
		return "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
	}

	private static String digest() {
		return UUID.randomUUID().toString().replace("-", "")
				+ UUID.randomUUID().toString().replace("-", "");
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("等待并发测试信号超时");
			}
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("并发测试被中断", interrupted);
		}
	}

	private record Fixture(
			long accountId,
			long sessionId,
			long refreshTokenId,
			String accessDigest,
			String refreshDigest) {
	}

	private static final class ExpectedRollback extends RuntimeException {
	}
}
