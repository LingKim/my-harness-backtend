package com.heness.project.account.application;

import com.heness.project.account.domain.PreferredLanguage;
import com.heness.project.account.domain.TravelContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public class AccountPreferencesService {
	private final AccountPreferencesRepository repository;
	private final Clock clock;

	public AccountPreferencesService(AccountPreferencesRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public TravelContext getTravelContext(UUID accountId) {
		return repository.findTravelContext(accountId);
	}

	@Transactional
	public TravelContext replaceTravelContext(
			UUID accountId, String countryOrRegion, String city, LocalDate tripStartDate,
			LocalDate tripEndDate, List<String> restrictions, String assistanceNeeds, long version) {
		TravelContext replacement = TravelContext.replace(countryOrRegion, city, tripStartDate,
				tripEndDate, restrictions, assistanceNeeds, version);
		return repository.replaceTravelContext(accountId, replacement, clock.instant());
	}

	@Transactional
	public void clearTravelContext(UUID accountId, long version) {
		repository.clearTravelContext(accountId, version, clock.instant());
	}

	@Transactional
	public AccountView updatePreferredLanguage(UUID accountId, String value) {
		PreferredLanguage language = PreferredLanguage.fromWireValue(value);
		return repository.updatePreferredLanguage(accountId, language.wireValue(), clock.instant());
	}
}
