package com.heness.project.account.infrastructure.security;

import com.heness.project.account.application.PasswordService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
final class BcryptPasswordService implements PasswordService {

	private final PasswordEncoder passwordEncoder;
	private final String dummyHash;

	BcryptPasswordService(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
		this.dummyHash = passwordEncoder.encode("fixed-dummy-password-2026");
	}

	@Override
	public String encode(String rawPassword) {
		return passwordEncoder.encode(rawPassword);
	}

	@Override
	public boolean matchesOrDummy(String rawPassword, String encodedPassword) {
		return passwordEncoder.matches(rawPassword, encodedPassword == null ? dummyHash : encodedPassword);
	}
}
