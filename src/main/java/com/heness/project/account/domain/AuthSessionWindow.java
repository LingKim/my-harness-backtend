package com.heness.project.account.domain;

import java.time.Duration;
import java.time.Instant;

public record AuthSessionWindow(Instant accessExpiresAt, Instant absoluteExpiresAt) {

	private static final Duration ACCESS_TTL = Duration.ofMinutes(30);
	private static final Duration SESSION_TTL = Duration.ofDays(7);

	public static AuthSessionWindow start(Instant createdAt) {
		return new AuthSessionWindow(createdAt.plus(ACCESS_TTL), createdAt.plus(SESSION_TTL));
	}

	public AuthSessionWindow refresh(Instant refreshedAt) {
		Instant nextAccessExpiry = refreshedAt.plus(ACCESS_TTL);
		if (nextAccessExpiry.isAfter(absoluteExpiresAt)) {
			nextAccessExpiry = absoluteExpiresAt;
		}
		return new AuthSessionWindow(nextAccessExpiry, absoluteExpiresAt);
	}
}
