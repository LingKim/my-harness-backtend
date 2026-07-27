package com.heness.project.account.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

final class DatabaseTimes {

	private DatabaseTimes() {
	}

	static LocalDateTime toDatabase(Instant instant) {
		return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	static Instant toInstant(LocalDateTime value) {
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}
}
