package com.heness.project.account.application;

import java.time.Instant;

public record CurrentSessionRecord(long sessionId, Instant absoluteExpiresAt) {
}
