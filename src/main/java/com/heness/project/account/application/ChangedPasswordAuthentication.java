package com.heness.project.account.application;

public record ChangedPasswordAuthentication(IssuedAuthentication authentication, SecretToken csrfToken) {
	@Override
	public String toString() {
		return "ChangedPasswordAuthentication[credentials=redacted]";
	}
}
