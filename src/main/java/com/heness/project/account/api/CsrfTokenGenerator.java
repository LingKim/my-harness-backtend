package com.heness.project.account.api;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
final class CsrfTokenGenerator {
	private final SecureRandom random = new SecureRandom();

	String generate() {
		byte[] value = new byte[32];
		random.nextBytes(value);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}
}
