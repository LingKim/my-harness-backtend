package com.heness.project.account.api;

import com.heness.project.account.infrastructure.security.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTests {

	@Test
	void 非可信来源忽略伪造转发头() {
		ClientIpResolver resolver = new ClientIpResolver(properties(List.of("10.0.0.0/8")));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.10");
		request.addHeader("X-Forwarded-For", "198.51.100.9");

		assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
	}

	@Test
	void 可信代理只采用转发链第一个合法地址() {
		ClientIpResolver resolver = new ClientIpResolver(properties(List.of("10.0.0.0/8")));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.1.2.3");
		request.addHeader("X-Forwarded-For", "198.51.100.9, 10.1.2.3");

		assertThat(resolver.resolve(request)).isEqualTo("198.51.100.9");
	}

	private AuthProperties properties(List<String> trustedCidrs) {
		return new AuthProperties(
				true,
				"test-token-pepper-32-characters-minimum",
				"test-failure-pepper-32-characters-minimum",
				Duration.ofMinutes(30),
				Duration.ofDays(7),
				Duration.ofSeconds(5),
				12,
				true,
				trustedCidrs
		);
	}
}
