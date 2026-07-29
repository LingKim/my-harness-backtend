package com.heness.project.account.application;

import java.time.Instant;
import java.util.UUID;

public record AccountView(UUID accountId, String accountName, Instant createdAt, String preferredLanguage) {
	public AccountView(UUID accountId, String accountName, Instant createdAt) {
		this(accountId, accountName, createdAt, "zh-CN");
	}
}
