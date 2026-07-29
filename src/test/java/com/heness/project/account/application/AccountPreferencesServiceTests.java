package com.heness.project.account.application;

import com.heness.project.account.domain.TravelContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountPreferencesServiceTests {
	private static final Instant NOW = Instant.parse("2026-07-29T08:00:00Z");
	private static final UUID ACCOUNT_ID = UUID.fromString("78cd6aa7-7bc0-4d71-b1ab-e26f4fdf570f");

	@Test
	void replacementUsesAuthenticatedAccountAndNormalizedWholeResource() {
		FakeRepository repository = new FakeRepository();
		AccountPreferencesService service = new AccountPreferencesService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

		TravelContext result = service.replaceTravelContext(ACCOUNT_ID, null, " Shanghai ", null, null,
				List.of(" Vegan ", "vegan"), null, 0);

		assertThat(repository.accountId).isEqualTo(ACCOUNT_ID);
		assertThat(result.city()).isEqualTo("Shanghai");
		assertThat(result.dietaryRestrictions()).containsExactly("Vegan");
	}

	@Test
	void invalidLanguageDoesNotReachPersistence() {
		FakeRepository repository = new FakeRepository();
		AccountPreferencesService service = new AccountPreferencesService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
		assertThatThrownBy(() -> service.updatePreferredLanguage(ACCOUNT_ID, "zh-TW"))
				.isInstanceOf(RuntimeException.class);
		assertThat(repository.language).isNull();
	}

	private static final class FakeRepository implements AccountPreferencesRepository {
		UUID accountId;
		String language;
		@Override public TravelContext findTravelContext(UUID accountId) { return TravelContext.empty(0); }
		@Override public TravelContext replaceTravelContext(UUID accountId, TravelContext replacement, Instant now) {
			this.accountId = accountId;
			return new TravelContext(replacement.countryOrRegion(), replacement.city(), replacement.tripStartDate(),
					replacement.tripEndDate(), replacement.dietaryRestrictions(), replacement.assistanceNeeds(), 1);
		}
		@Override public TravelContext clearTravelContext(UUID accountId, long expectedVersion, Instant now) {
			return TravelContext.empty(expectedVersion + 1);
		}
		@Override public AccountView updatePreferredLanguage(UUID accountId, String preferredLanguage, Instant now) {
			language = preferredLanguage;
			return new AccountView(accountId, "China_2026", now, preferredLanguage);
		}
	}
}
