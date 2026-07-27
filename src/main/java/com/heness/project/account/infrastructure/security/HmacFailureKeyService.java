package com.heness.project.account.infrastructure.security;

import com.heness.project.account.application.FailureKeyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
final class HmacFailureKeyService implements FailureKeyService {

	private final String pepper;

	HmacFailureKeyService(AuthProperties properties) {
		this.pepper = properties.failurePepper();
	}

	@Override
	public String accountKey(String normalizedAccountName) {
		return HmacDigests.sha256(pepper, "account:" + normalizedAccountName);
	}

	@Override
	public String ipKey(String clientIp) {
		return HmacDigests.sha256(pepper, "ip:" + clientIp);
	}
}
