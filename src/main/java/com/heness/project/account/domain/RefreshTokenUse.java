package com.heness.project.account.domain;

import java.time.Duration;
import java.time.Instant;

public enum RefreshTokenUse {
	CONCURRENT_CONFLICT,
	REPLAY;

	private static final Duration CONFLICT_GRACE = Duration.ofSeconds(5);

	public static RefreshTokenUse classifyRotated(Instant rotatedAt, Instant usedAt) {
		return usedAt.isAfter(rotatedAt.plus(CONFLICT_GRACE)) ? REPLAY : CONCURRENT_CONFLICT;
	}
}
