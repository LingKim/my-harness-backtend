package com.heness.project.account.application;

import java.time.Duration;

public final class AuthFailure extends RuntimeException {

	private final AuthFailureReason reason;
	private final Duration retryAfter;

	private AuthFailure(AuthFailureReason reason, Duration retryAfter) {
		super(reason.name());
		this.reason = reason;
		this.retryAfter = retryAfter;
	}

	public static AuthFailure of(AuthFailureReason reason) {
		return new AuthFailure(reason, null);
	}

	public static AuthFailure rateLimited(Duration retryAfter) {
		return new AuthFailure(AuthFailureReason.RATE_LIMITED, retryAfter);
	}

	public AuthFailureReason reason() {
		return reason;
	}

	public Duration retryAfter() {
		return retryAfter;
	}
}
