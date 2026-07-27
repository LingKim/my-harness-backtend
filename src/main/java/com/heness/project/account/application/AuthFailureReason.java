package com.heness.project.account.application;

public enum AuthFailureReason {
	REGISTRATION_REJECTED,
	INVALID_CREDENTIALS,
	RATE_LIMITED,
	ACCESS_TOKEN_INVALID,
	REFRESH_TOKEN_INVALID,
	REFRESH_CONFLICT
}
