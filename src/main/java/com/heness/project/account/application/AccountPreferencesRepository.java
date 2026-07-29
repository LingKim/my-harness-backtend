package com.heness.project.account.application;

import com.heness.project.account.domain.TravelContext;

import java.time.Instant;
import java.util.UUID;

public interface AccountPreferencesRepository {
	TravelContext findTravelContext(UUID accountId);
	TravelContext replaceTravelContext(UUID accountId, TravelContext replacement, Instant now);
	TravelContext clearTravelContext(UUID accountId, long expectedVersion, Instant now);
	AccountView updatePreferredLanguage(UUID accountId, String preferredLanguage, Instant now);
}
