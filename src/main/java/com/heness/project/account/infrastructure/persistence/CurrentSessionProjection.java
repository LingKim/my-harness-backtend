package com.heness.project.account.infrastructure.persistence;

import java.time.LocalDateTime;

record CurrentSessionProjection(Long sessionId, LocalDateTime absoluteExpiresAt) {
}
