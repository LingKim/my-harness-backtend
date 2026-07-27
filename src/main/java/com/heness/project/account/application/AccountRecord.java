package com.heness.project.account.application;

import java.time.Instant;
import java.util.UUID;

public record AccountRecord(
		long internalId,
		UUID publicId,
		String accountName,
		String normalizedAccountName,
		String passwordHash,
		Instant createdAt) {

	public AccountView toView() {
		return new AccountView(publicId, accountName, createdAt);
	}

	@Override
	public String toString() {
		return "AccountRecord[internalId=" + internalId + ", publicId=" + publicId + ", sensitive=redacted]";
	}
}
