package com.heness.project.account.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TravelContextTests {

	@Test
	void normalizesOptionalTextAndDeduplicatesRestrictionsInFirstSeenOrder() {
		TravelContext context = TravelContext.replace(
				"  China  ", " Shanghai ", LocalDate.parse("2026-08-01"),
				LocalDate.parse("2026-08-03"), List.of(" Vegan ", "vegan", "No Nuts"), " Wheelchair ", 4);

		assertThat(context.countryOrRegion()).isEqualTo("China");
		assertThat(context.dietaryRestrictions()).containsExactly("Vegan", "No Nuts");
		assertThat(context.version()).isEqualTo(4);
	}

	@Test
	void fullUnicodeCaseFoldingDeduplicatesSharpSInFirstSeenOrder() {
		TravelContext context = TravelContext.replace(
				null, null, null, null, List.of("Straße", "STRASSE", "No Nuts"), null, 0);

		assertThat(context.dietaryRestrictions()).containsExactly("Straße", "No Nuts");
		assertThat(TravelContext.restrictionKey("Straße")).isEqualTo("strasse");
		assertThat(TravelContext.restrictionKey("STRASSE")).isEqualTo("strasse");
	}

	@Test
	void rejectsEmptyTextAndReversedDates() {
		assertThatThrownBy(() -> TravelContext.replace(" ", null, null, null, List.of(), null, 0))
				.isInstanceOf(TravelContextViolation.class);
		assertThatThrownBy(() -> TravelContext.replace(null, null, LocalDate.parse("2026-08-02"),
				LocalDate.parse("2026-08-01"), List.of(), null, 0))
				.isInstanceOf(TravelContextViolation.class);
	}

	@Test
	void enforcesRestrictionCountAndUnicodeCodePointLengths() {
		assertThatThrownBy(() -> TravelContext.replace(null, null, null, null,
				java.util.stream.IntStream.range(0, 21).mapToObj(String::valueOf).toList(), null, 0))
				.isInstanceOf(TravelContextViolation.class);
		assertThatThrownBy(() -> TravelContext.replace("a".repeat(101), null, null, null, List.of(), null, 0))
				.isInstanceOf(TravelContextViolation.class);
	}
}
