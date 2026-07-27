package com.heness.project.account.api;

final class AuthCookies {
	static final String ACCESS = "__Host-cm_access";
	static final String REFRESH = "__Host-cm_refresh";
	static final String CSRF = "cm_csrf";
	static final String CSRF_HEADER = "X-CSRF-Token";

	private AuthCookies() {
	}
}
