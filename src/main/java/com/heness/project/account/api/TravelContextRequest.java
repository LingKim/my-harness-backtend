package com.heness.project.account.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

record TravelContextRequest(
		String countryOrRegion,
		String city,
		LocalDate tripStartDate,
		LocalDate tripEndDate,
		@Size(max = 20) List<@NotNull String> dietaryRestrictions,
		String assistanceNeeds,
		@NotNull @PositiveOrZero Long version) {
	TravelContextRequest {
		dietaryRestrictions = dietaryRestrictions == null ? List.of() : List.copyOf(dietaryRestrictions);
	}
}
