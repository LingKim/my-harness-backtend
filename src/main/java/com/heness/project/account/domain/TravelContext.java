package com.heness.project.account.domain;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record TravelContext(
		String countryOrRegion,
		String city,
		LocalDate tripStartDate,
		LocalDate tripEndDate,
		List<String> dietaryRestrictions,
		String assistanceNeeds,
		long version) {

	public TravelContext {
		dietaryRestrictions = List.copyOf(dietaryRestrictions);
		if (version < 0) {
			throw new TravelContextViolation("VERSION_INVALID");
		}
	}

	public static TravelContext empty(long version) {
		return new TravelContext(null, null, null, null, List.of(), null, version);
	}

	public static TravelContext replace(
			String countryOrRegion,
			String city,
			LocalDate tripStartDate,
			LocalDate tripEndDate,
			List<String> dietaryRestrictions,
			String assistanceNeeds,
			long version) {
		String country = normalizeOptional(countryOrRegion, 100, "COUNTRY_OR_REGION_INVALID");
		String normalizedCity = normalizeOptional(city, 100, "CITY_INVALID");
		String needs = normalizeOptional(assistanceNeeds, 1000, "ASSISTANCE_NEEDS_INVALID");
		if (tripStartDate != null && tripEndDate != null && tripEndDate.isBefore(tripStartDate)) {
			throw new TravelContextViolation("TRIP_DATE_ORDER_INVALID");
		}
		return new TravelContext(country, normalizedCity, tripStartDate, tripEndDate,
				normalizeRestrictions(dietaryRestrictions), needs, version);
	}

	public boolean isEmpty() {
		return countryOrRegion == null && city == null && tripStartDate == null && tripEndDate == null
				&& dietaryRestrictions.isEmpty() && assistanceNeeds == null;
	}

	private static String normalizeOptional(String value, int maximum, String rule) {
		if (value == null) {
			return null;
		}
		String normalized = Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
		int length = normalized.codePointCount(0, normalized.length());
		if (length == 0 || length > maximum) {
			throw new TravelContextViolation(rule);
		}
		return normalized;
	}

	private static List<String> normalizeRestrictions(List<String> values) {
		if (values == null) {
			throw new TravelContextViolation("DIETARY_RESTRICTIONS_INVALID");
		}
		if (values.size() > 20) {
			throw new TravelContextViolation("DIETARY_RESTRICTIONS_TOO_MANY");
		}
		LinkedHashSet<String> keys = new LinkedHashSet<>();
		List<String> result = new ArrayList<>();
		for (String value : values) {
			String normalized = normalizeOptional(value, 80, "DIETARY_RESTRICTION_INVALID");
			String key = restrictionKey(normalized);
			if (key.codePointCount(0, key.length()) > 80) {
				throw new TravelContextViolation("DIETARY_RESTRICTION_INVALID");
			}
			if (keys.add(key)) {
				result.add(normalized);
			}
		}
		return result;
	}

	public static String restrictionKey(String value) {
		if (value == null) {
			throw new IllegalArgumentException("饮食限制不能为空");
		}
		return UnicodeCaseFold.fold(value);
	}
}
