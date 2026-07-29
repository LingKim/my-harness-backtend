package com.heness.project.account.infrastructure.persistence;

import com.heness.project.account.application.AccountPreferencesRepository;
import com.heness.project.account.application.AccountView;
import com.heness.project.account.application.TravelContextVersionConflict;
import com.heness.project.account.domain.TravelContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
class MybatisAccountPreferencesRepository implements AccountPreferencesRepository {
	private final TravelContextMapper travelMapper;
	private final DietaryRestrictionMapper dietaryMapper;
	private final AccountPreferencesMapper preferencesMapper;

	MybatisAccountPreferencesRepository(
			TravelContextMapper travelMapper,
			DietaryRestrictionMapper dietaryMapper,
			AccountPreferencesMapper preferencesMapper) {
		this.travelMapper = travelMapper;
		this.dietaryMapper = dietaryMapper;
		this.preferencesMapper = preferencesMapper;
	}

	@Override
	public TravelContext findTravelContext(UUID accountId) {
		TravelContextRow row = requireRow(accountId);
		List<String> restrictions = dietaryMapper.findOrdered(row.getAccountId()).stream()
				.map(DietaryRestrictionRow::getRestrictionText)
				.toList();
		return toDomain(row, restrictions);
	}

	@Override
	public TravelContext replaceTravelContext(UUID accountId, TravelContext replacement, Instant now) {
		TravelContextRow current = requireRow(accountId);
		int updated = travelMapper.replaceIfVersionMatches(
				current.getAccountId(), replacement.version(), replacement.countryOrRegion(), replacement.city(),
				replacement.tripStartDate(), replacement.tripEndDate(), replacement.assistanceNeeds(),
				DatabaseTimes.toDatabase(now));
		if (updated != 1) {
			throw conflict(current.getAccountId());
		}
		dietaryMapper.deleteForAccount(current.getAccountId());
		for (int position = 0; position < replacement.dietaryRestrictions().size(); position++) {
			String value = replacement.dietaryRestrictions().get(position);
			DietaryRestrictionRow row = new DietaryRestrictionRow();
			row.setAccountId(current.getAccountId());
			row.setPosition(position);
			row.setRestrictionText(value);
			row.setNormalizedText(TravelContext.restrictionKey(value));
			dietaryMapper.insert(row);
		}
		return new TravelContext(replacement.countryOrRegion(), replacement.city(), replacement.tripStartDate(),
				replacement.tripEndDate(), replacement.dietaryRestrictions(), replacement.assistanceNeeds(),
				replacement.version() + 1);
	}

	@Override
	public TravelContext clearTravelContext(UUID accountId, long expectedVersion, Instant now) {
		TravelContextRow current = requireRow(accountId);
		if (travelMapper.clearIfVersionMatches(current.getAccountId(), expectedVersion,
				DatabaseTimes.toDatabase(now)) != 1) {
			throw conflict(current.getAccountId());
		}
		dietaryMapper.deleteForAccount(current.getAccountId());
		return TravelContext.empty(expectedVersion + 1);
	}

	@Override
	public AccountView updatePreferredLanguage(UUID accountId, String preferredLanguage, Instant now) {
		if (preferencesMapper.updateLanguage(accountId.toString(), preferredLanguage,
				DatabaseTimes.toDatabase(now)) != 1) {
			throw new IllegalStateException("账号偏好不存在");
		}
		AccountAccessProjection row = preferencesMapper.findAccountView(accountId.toString());
		return new AccountView(UUID.fromString(row.publicId()), row.accountName(),
				DatabaseTimes.toInstant(row.createdAt()), row.preferredLanguage());
	}

	private TravelContextRow requireRow(UUID accountId) {
		TravelContextRow row = travelMapper.findByPublicAccountId(accountId.toString());
		if (row == null) {
			throw new IllegalStateException("账号旅行上下文不存在");
		}
		return row;
	}

	private TravelContextVersionConflict conflict(long internalAccountId) {
		Long version = travelMapper.lockCurrentVersion(internalAccountId);
		if (version == null) {
			throw new IllegalStateException("账号旅行上下文不存在");
		}
		return new TravelContextVersionConflict(version);
	}

	private TravelContext toDomain(TravelContextRow row, List<String> restrictions) {
		return new TravelContext(row.getCountryOrRegion(), row.getCity(), row.getTripStartDate(),
				row.getTripEndDate(), restrictions, row.getAssistanceNeeds(), row.getVersion());
	}
}
