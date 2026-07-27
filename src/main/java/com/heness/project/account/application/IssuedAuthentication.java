package com.heness.project.account.application;

import java.time.Instant;

public final class IssuedAuthentication {

	private final AccountView account;
	private final SecretToken accessToken;
	private final SecretToken refreshToken;
	private final Instant accessExpiresAt;
	private final Instant absoluteExpiresAt;

	public IssuedAuthentication(
			AccountView account,
			SecretToken accessToken,
			SecretToken refreshToken,
			Instant accessExpiresAt,
			Instant absoluteExpiresAt) {
		this.account = account;
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.accessExpiresAt = accessExpiresAt;
		this.absoluteExpiresAt = absoluteExpiresAt;
	}

	public AccountView account() {
		return account;
	}

	public SecretToken accessToken() {
		return accessToken;
	}

	public SecretToken refreshToken() {
		return refreshToken;
	}

	public Instant accessExpiresAt() {
		return accessExpiresAt;
	}

	public Instant absoluteExpiresAt() {
		return absoluteExpiresAt;
	}

	@Override
	public String toString() {
		return "IssuedAuthentication[accountId=" + account.accountId() + ", tokens=redacted]";
	}
}
