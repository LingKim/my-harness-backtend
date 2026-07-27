package com.heness.project.account.application;

import java.time.Instant;

public record RefreshTokenRecord(
		long tokenId,
		long sessionId,
		RefreshTokenStatus status,
		Instant rotatedAt,
		Instant expiresAt,
		Instant absoluteExpiresAt) {
}
