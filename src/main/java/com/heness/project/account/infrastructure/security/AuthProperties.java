package com.heness.project.account.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("app.auth")
public record AuthProperties(
		boolean enabled,
		String tokenPepper,
		String failurePepper,
		Duration accessTokenTtl,
		Duration sessionTtl,
		Duration refreshConflictGrace,
		int bcryptCost,
		boolean cookieSecure,
		List<String> trustedProxyCidrs) {

	public AuthProperties {
		if (!enabled) {
			throw new IllegalArgumentException("AuthProperties 只应在认证启用时创建");
		}
		requireSecret("app.auth.token-pepper", tokenPepper);
		requireSecret("app.auth.failure-pepper", failurePepper);
		if (!Duration.ofMinutes(30).equals(accessTokenTtl)) {
			throw new IllegalArgumentException("app.auth.access-token-ttl 必须为 30 分钟");
		}
		if (!Duration.ofDays(7).equals(sessionTtl)) {
			throw new IllegalArgumentException("app.auth.session-ttl 必须为 7 天");
		}
		if (!Duration.ofSeconds(5).equals(refreshConflictGrace)) {
			throw new IllegalArgumentException("app.auth.refresh-conflict-grace 必须为 5 秒");
		}
		if (bcryptCost < 12 || bcryptCost > 16) {
			throw new IllegalArgumentException("app.auth.bcrypt-cost 必须在 12 到 16 之间");
		}
		trustedProxyCidrs = trustedProxyCidrs == null ? List.of() : List.copyOf(trustedProxyCidrs);
	}

	private static void requireSecret(String propertyName, String value) {
		if (value == null || value.length() < 32 || value.startsWith("replace-with-")) {
			throw new IllegalArgumentException(propertyName + " 必须是至少 32 字符的非占位随机秘密");
		}
	}
}
