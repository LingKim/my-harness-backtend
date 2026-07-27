package com.heness.project.account.infrastructure.security;

import com.heness.project.account.application.SecretToken;
import com.heness.project.account.application.TokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
final class OpaqueTokenService implements TokenService {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();
	private final String pepper;

	OpaqueTokenService(AuthProperties properties) {
		this.pepper = properties.tokenPepper();
	}

	@Override
	public SecretToken issue() {
		byte[] random = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(random);
		String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
		return new SecretToken(raw, digest(raw));
	}

	@Override
	public String digest(String rawToken) {
		return HmacDigests.sha256(pepper, rawToken);
	}
}
