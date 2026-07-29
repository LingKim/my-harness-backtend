package com.heness.project.account.api;

import com.heness.project.account.domain.TravelContext;

import java.time.LocalDate;
import java.util.List;

record TravelContextResponse(
		String countryOrRegion, String city, LocalDate tripStartDate, LocalDate tripEndDate,
		List<String> dietaryRestrictions, String assistanceNeeds, long version) {
	static TravelContextResponse from(TravelContext value) {
		return new TravelContextResponse(value.countryOrRegion(), value.city(), value.tripStartDate(),
				value.tripEndDate(), value.dietaryRestrictions(), value.assistanceNeeds(), value.version());
	}
}
