package com.heness.project.account.application;

public final class SecretToken {

	private final String rawValue;
	private final String digest;

	public SecretToken(String rawValue, String digest) {
		this.rawValue = rawValue;
		this.digest = digest;
	}

	public String rawValue() {
		return rawValue;
	}

	public String digest() {
		return digest;
	}

	@Override
	public String toString() {
		return "SecretToken[redacted]";
	}
}
