package com.heness.project.account.infrastructure.persistence;

import java.time.LocalDateTime;

record AccountAccessProjection(String publicId, String accountName, LocalDateTime createdAt) {
}
