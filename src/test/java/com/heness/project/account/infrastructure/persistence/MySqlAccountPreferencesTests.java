package com.heness.project.account.infrastructure.persistence;

import com.heness.project.account.application.AccountAuthenticationService;
import com.heness.project.account.application.AuthenticationRepository;
import com.heness.project.account.application.PasswordService;
import com.heness.project.account.application.TokenService;
import com.heness.project.account.application.AccountPreferencesService;
import com.heness.project.account.application.AuthFailure;
import com.heness.project.account.application.IssuedAuthentication;
import com.heness.project.account.application.TravelContextVersionConflict;
import com.heness.project.account.domain.TravelContext;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DATABASE_ENABLED", matches = "true")
class MySqlAccountPreferencesTests {
	private static final String PASSWORD = "Travel2026!";

	@Autowired private Flyway flyway;
	@Autowired private DataSource dataSource;
	@Autowired private AccountAuthenticationService authentication;
	@Autowired private AccountPreferencesService preferences;
	@Autowired private MybatisAccountPreferencesRepository repository;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private AuthenticationRepository authenticationRepository;
	@Autowired private PasswordService passwordService;
	@Autowired private TokenService tokenService;
	@Autowired private MockMvc mockMvc;

	@Test
	void flywayVersionThreeCreatesPreferenceTablesAndBackfillsDefaults() throws Exception {
		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
		assertThat(tableNames()).contains("account_preferences", "account_travel_context", "account_dietary_restriction");
	}

	@Test
	void newlyRegisteredAccountGetsStableEmptyTravelContextOverHttp() throws Exception {
		Fixture fixture = register();
		try {
			mockMvc.perform(get("/api/v1/accounts/me/travel-context")
					.cookie(new Cookie("__Host-cm_access", fixture.authentication().accessToken().rawValue())))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.countryOrRegion").doesNotExist())
					.andExpect(jsonPath("$.city").doesNotExist())
					.andExpect(jsonPath("$.dietaryRestrictions").isEmpty())
					.andExpect(jsonPath("$.version").value(0));
			assertThat(authentication.currentAccount(fixture.authentication().accessToken().rawValue())
					.preferredLanguage()).isEqualTo("zh-CN");
		} finally {
			cleanup(fixture.accountName());
		}
	}

	@Test
	void travelReplacementIsAccountScopedOrderedAndOptimisticallyVersioned() throws Exception {
		Fixture first = register();
		Fixture second = register();
		try {
			TravelContext saved = preferences.replaceTravelContext(first.accountId(), " China ", " Shanghai ",
					LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-03"),
					List.of(" Vegan ", "vegan", "No Nuts"), " Wheelchair ", 0);
			assertThat(saved.version()).isEqualTo(1);
			assertThat(preferences.getTravelContext(first.accountId()).dietaryRestrictions())
					.containsExactly("Vegan", "No Nuts");
			assertThat(preferences.getTravelContext(second.accountId())).isEqualTo(TravelContext.empty(0));

			assertThatThrownBy(() -> preferences.clearTravelContext(first.accountId(), 0))
					.isInstanceOfSatisfying(TravelContextVersionConflict.class,
							failure -> assertThat(failure.latestVersion()).isEqualTo(1));
			assertThat(preferences.getTravelContext(first.accountId()).city()).isEqualTo("Shanghai");

			preferences.clearTravelContext(first.accountId(), 1);
			assertThat(preferences.getTravelContext(first.accountId())).isEqualTo(TravelContext.empty(2));
			preferences.clearTravelContext(first.accountId(), 2);
			assertThat(preferences.getTravelContext(first.accountId())).isEqualTo(TravelContext.empty(3));
		} finally {
			cleanup(first.accountName(), second.accountName());
		}
	}

	@Test
	void unicodeCaseFoldedRestrictionsPersistOneKeyAndKeepFirstSeenOrder() throws Exception {
		Fixture fixture = register();
		try {
			preferences.replaceTravelContext(fixture.accountId(), null, null, null, null,
					List.of("Straße", "STRASSE", "No Nuts"), null, 0);

			assertThat(preferences.getTravelContext(fixture.accountId()).dietaryRestrictions())
					.containsExactly("Straße", "No Nuts");
			assertThat(normalizedRestrictions(fixture.accountName())).containsExactly("strasse", "no nuts");
			assertDuplicateNormalizedKeyRejected(fixture.accountName(), "STRASSE", "strasse");
		} finally {
			cleanup(fixture.accountName());
		}
	}

	@Test
	void transactionRollbackRestoresMainAndRestrictions() throws Exception {
		Fixture fixture = register();
		try {
			preferences.replaceTravelContext(fixture.accountId(), null, "Before", null, null,
					List.of("Before restriction"), null, 0);
			TransactionTemplate transaction = new TransactionTemplate(transactionManager);
			assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
				repository.replaceTravelContext(fixture.accountId(),
						TravelContext.replace(null, "After", null, null, List.of("After restriction"), null, 1),
						Instant.now());
				throw new ExpectedRollback();
			})).isInstanceOf(ExpectedRollback.class);
			TravelContext restored = preferences.getTravelContext(fixture.accountId());
			assertThat(restored.city()).isEqualTo("Before");
			assertThat(restored.dietaryRestrictions()).containsExactly("Before restriction");
			assertThat(restored.version()).isEqualTo(1);
		} finally {
			cleanup(fixture.accountName());
		}
	}

	@Test
	void passwordChangeRevokesOtherSessionRotatesCurrentAndKeepsAbsoluteDeadline() throws Exception {
		Fixture fixture = register();
		try {
			IssuedAuthentication other = authentication.login(fixture.accountName(), PASSWORD, "203.0.113.52");
			Instant originalDeadline = other.absoluteExpiresAt();
			var changed = authentication.changePassword(fixture.accountId(), other.accessToken().rawValue(),
					PASSWORD, "NewTravel2027!", "NewTravel2027!");

			assertThat(changed.authentication().absoluteExpiresAt()).isEqualTo(originalDeadline);
			assertThat(authentication.currentAccount(changed.authentication().accessToken().rawValue()).accountId())
					.isEqualTo(fixture.accountId());
			assertThatThrownBy(() -> authentication.currentAccount(fixture.authentication().accessToken().rawValue()))
					.isInstanceOf(AuthFailure.class);
			assertThatThrownBy(() -> authentication.currentAccount(other.accessToken().rawValue()))
					.isInstanceOf(AuthFailure.class);
			assertThatThrownBy(() -> authentication.refresh(other.refreshToken().rawValue()))
					.isInstanceOf(AuthFailure.class);
			assertThat(authentication.refresh(changed.authentication().refreshToken().rawValue()).absoluteExpiresAt())
					.isEqualTo(originalDeadline);
			assertThatThrownBy(() -> authentication.login(fixture.accountName(), PASSWORD, "203.0.113.53"))
					.isInstanceOf(AuthFailure.class);
			assertThat(authentication.login(fixture.accountName(), "NewTravel2027!", "203.0.113.54").account().accountId())
					.isEqualTo(fixture.accountId());
		} finally {
			cleanup(fixture.accountName());
		}
	}

	@Test
	void schemaRejectsInvalidLanguageDateQuantityAndForeignKeyValues() throws Exception {
		Fixture fixture = register();
		try {
			assertSqlFailure("UPDATE account_preferences SET preferred_language = 'zh-TW' WHERE account_id = "
					+ internalAccountId(fixture.accountName()));
			assertSqlFailure("UPDATE account_travel_context SET trip_start_date = '2026-08-02', "
					+ "trip_end_date = '2026-08-01' WHERE account_id = " + internalAccountId(fixture.accountName()));
			assertSqlFailure("INSERT INTO account_dietary_restriction "
					+ "(account_id, position, restriction_text, normalized_text) VALUES ("
					+ internalAccountId(fixture.accountName()) + ", 20, 'x', 'x')");
			assertSqlFailure("INSERT INTO account_dietary_restriction "
					+ "(account_id, position, restriction_text, normalized_text) VALUES (18446744073709551614, 0, 'x', 'x')");
		} finally {
			cleanup(fixture.accountName());
		}
	}

	@Test
	void passwordAndSessionPersistenceRollsBackTogetherOnFailure() throws Exception {
		Fixture fixture = register();
		try {
			TransactionTemplate transaction = new TransactionTemplate(transactionManager);
			assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
				var account = authenticationRepository.lockAccountByPublicId(fixture.accountId()).orElseThrow();
				var current = authenticationRepository.lockActiveSession(account.internalId(),
						tokenService.digest(fixture.authentication().accessToken().rawValue()), Instant.now()).orElseThrow();
				var access = tokenService.issue();
				var refresh = tokenService.issue();
				authenticationRepository.updatePasswordAndRotateCurrentSession(account, current,
						passwordService.encode("Rollback2027!"), access.digest(), refresh.digest(),
						Instant.now().plus(Duration.ofMinutes(30)), Instant.now());
				throw new ExpectedRollback();
			})).isInstanceOf(ExpectedRollback.class);
			assertThat(authentication.currentAccount(fixture.authentication().accessToken().rawValue()).accountId())
					.isEqualTo(fixture.accountId());
			assertThat(authentication.login(fixture.accountName(), PASSWORD, "203.0.113.61").account().accountId())
					.isEqualTo(fixture.accountId());
		} finally {
			cleanup(fixture.accountName());
		}
	}

	private Fixture register() {
		String name = "tp" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		IssuedAuthentication issued = authentication.register(name, PASSWORD, PASSWORD, true);
		return new Fixture(name, issued.account().accountId(), issued);
	}

	private Set<String> tableNames() throws Exception {
		java.util.HashSet<String> names = new java.util.HashSet<>();
		try (Connection connection = dataSource.getConnection();
			 ResultSet rows = connection.getMetaData().getTables(connection.getCatalog(), null, "account%", new String[]{"TABLE"})) {
			while (rows.next()) names.add(rows.getString("TABLE_NAME"));
		}
		return names;
	}

	private long internalAccountId(String accountName) throws Exception {
		try (Connection connection = dataSource.getConnection();
			 PreparedStatement statement = connection.prepareStatement(
					 "SELECT id FROM account WHERE normalized_account_name = ?")) {
			statement.setString(1, accountName.toLowerCase());
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) throw new IllegalStateException("测试账号不存在");
				return rows.getLong(1);
			}
		}
	}

	private List<String> normalizedRestrictions(String accountName) throws Exception {
		try (Connection connection = dataSource.getConnection();
			 PreparedStatement statement = connection.prepareStatement("""
					 SELECT r.normalized_text
					 FROM account_dietary_restriction r
					 JOIN account a ON a.id = r.account_id
					 WHERE a.normalized_account_name = ?
					 ORDER BY r.position
					 """)) {
			statement.setString(1, accountName.toLowerCase());
			try (ResultSet rows = statement.executeQuery()) {
				java.util.ArrayList<String> values = new java.util.ArrayList<>();
				while (rows.next()) values.add(rows.getString(1));
				return values;
			}
		}
	}

	private void assertDuplicateNormalizedKeyRejected(
			String accountName, String restrictionText, String normalizedText) throws Exception {
		assertThatThrownBy(() -> {
			try (Connection connection = dataSource.getConnection();
				 PreparedStatement statement = connection.prepareStatement("""
						 INSERT INTO account_dietary_restriction
						 (account_id, position, restriction_text, normalized_text)
						 VALUES (?, ?, ?, ?)
						 """)) {
				statement.setLong(1, internalAccountId(accountName));
				statement.setInt(2, 2);
				statement.setString(3, restrictionText);
				statement.setString(4, normalizedText);
				statement.executeUpdate();
			}
		}).isInstanceOf(java.sql.SQLException.class);
	}

	private void assertSqlFailure(String sql) {
		assertThatThrownBy(() -> {
			try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
				statement.executeUpdate();
			}
		}).isInstanceOf(java.sql.SQLException.class);
	}

	private void cleanup(String... accountNames) throws Exception {
		try (Connection connection = dataSource.getConnection()) {
			for (String accountName : accountNames) {
				execute(connection, """
						DELETE r FROM account_refresh_token r
						JOIN account_auth_session s ON s.id = r.session_id
						JOIN account a ON a.id = s.account_id WHERE a.normalized_account_name = ?
						""", accountName.toLowerCase());
				execute(connection, """
						DELETE s FROM account_auth_session s JOIN account a ON a.id = s.account_id
						WHERE a.normalized_account_name = ?
						""", accountName.toLowerCase());
				execute(connection, "DELETE FROM account WHERE normalized_account_name = ?", accountName.toLowerCase());
			}
		}
	}

	private void execute(Connection connection, String sql, String value) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, value);
			statement.executeUpdate();
		}
	}

	private record Fixture(String accountName, UUID accountId, IssuedAuthentication authentication) { }
	private static final class ExpectedRollback extends RuntimeException { }
}
