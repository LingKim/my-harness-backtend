package com.heness.project.account.infrastructure.persistence;

import java.time.LocalDateTime;

record LoginFailureRow(
		Long id,
		String keyType,
		String keyHash,
		LocalDateTime windowStartedAt,
		int failureCount,
		LocalDateTime blockedUntil,
		LocalDateTime updatedAt) {
}
