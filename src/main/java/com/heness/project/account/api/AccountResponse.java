package com.heness.project.account.api;

import com.heness.project.account.application.AccountView;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID accountId, String accountName, Instant createdAt) {

	static AccountResponse from(AccountView account) {
		return new AccountResponse(account.accountId(), account.accountName(), account.createdAt());
	}
}
