package com.heness.project.account.infrastructure.security;

import com.heness.project.account.application.SecretToken;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPrimitiveTests {

	private final AuthProperties properties = new AuthProperties(
			true,
			"test-token-pepper-32-characters-minimum",
			"test-failure-pepper-32-characters-minimum",
			Duration.ofMinutes(30),
			Duration.ofDays(7),
			Duration.ofSeconds(5),
			12,
			true,
			List.of()
	);

	@Test
	void 不透明Token至少二百五十六位熵且只暴露摘要给持久化() {
		OpaqueTokenService service = new OpaqueTokenService(properties);

		SecretToken first = service.issue();
		SecretToken second = service.issue();

		assertThat(first.rawValue()).hasSize(43).isNotEqualTo(second.rawValue());
		assertThat(first.digest()).hasSize(64).isNotEqualTo(first.rawValue());
		assertThat(service.digest(first.rawValue())).isEqualTo(first.digest());
		assertThat(first.toString()).isEqualTo("SecretToken[redacted]");
	}

	@Test
	void 账号和IP失败键使用分域不可逆摘要() {
		HmacFailureKeyService service = new HmacFailureKeyService(properties);

		String accountKey = service.accountKey("china_2026");
		String ipKey = service.ipKey("127.0.0.1");

		assertThat(accountKey).hasSize(64).doesNotContain("china_2026");
		assertThat(ipKey).hasSize(64).doesNotContain("127.0.0.1").isNotEqualTo(accountKey);
	}

	@Test
	void 密码服务对不存在账号执行虚拟摘要校验且不泄露原文() {
		BcryptPasswordService service = new BcryptPasswordService(new BCryptPasswordEncoder(4));

		String encoded = service.encode("Password123");

		assertThat(encoded).doesNotContain("Password123");
		assertThat(service.matchesOrDummy("Password123", encoded)).isTrue();
		assertThat(service.matchesOrDummy("Password123", null)).isFalse();
	}
}
