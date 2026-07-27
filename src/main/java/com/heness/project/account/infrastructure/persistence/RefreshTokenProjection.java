package com.heness.project.account.infrastructure.persistence;

import java.time.LocalDateTime;

record RefreshTokenProjection(
		Long tokenId,
		Long sessionId,
		String status,
		LocalDateTime rotatedAt,
		LocalDateTime expiresAt,
		LocalDateTime absoluteExpiresAt) {
}
