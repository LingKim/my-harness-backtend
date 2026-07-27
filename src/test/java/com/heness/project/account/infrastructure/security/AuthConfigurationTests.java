package com.heness.project.account.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class AuthConfigurationTests {

	private static final String TOKEN_PEPPER = "test-token-pepper-32-characters-minimum";
	private static final String FAILURE_PEPPER = "test-failure-pepper-32-characters-minimum";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
			.withUserConfiguration(AuthConfiguration.class);

	@Test
	void 数据库关闭时不创建认证配置或密码编码器() {
		contextRunner.withPropertyValues("app.auth.enabled=false")
				.run(context -> {
					assertThat(context).doesNotHaveBean(AuthProperties.class);
					assertThat(context).doesNotHaveBean(PasswordEncoder.class);
				});
	}

	@Test
	void 认证开启且缺少秘密时启动失败() {
		contextRunner.withPropertyValues(
				"app.auth.enabled=true",
				"app.auth.access-token-ttl=30m",
				"app.auth.session-ttl=7d",
				"app.auth.refresh-conflict-grace=5s",
				"app.auth.bcrypt-cost=12"
		).run(context -> assertThat(context).hasFailed());
	}

	@Test
	void 认证开启时绑定固定时长和安全基线() {
		contextRunner.withPropertyValues(validProperties())
				.run(context -> {
					assertThat(context).hasNotFailed();
					AuthProperties properties = context.getBean(AuthProperties.class);
					assertThat(properties.accessTokenTtl()).hasMinutes(30);
					assertThat(properties.sessionTtl()).hasDays(7);
					assertThat(properties.refreshConflictGrace()).hasSeconds(5);
					assertThat(properties.cookieSecure()).isTrue();

					PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
					String encoded = encoder.encode("local-test-password-123");
					assertThat(encoded).doesNotContain("local-test-password-123");
					assertThat(encoder.matches("local-test-password-123", encoded)).isTrue();
				});
	}

	@Test
	void 不允许降低BCrypt成本() {
		contextRunner.withPropertyValues(validProperties())
				.withPropertyValues("app.auth.bcrypt-cost=11")
				.run(context -> assertThat(context).hasFailed());
	}

	private String[] validProperties() {
		return new String[] {
				"app.auth.enabled=true",
				"app.auth.token-pepper=" + TOKEN_PEPPER,
				"app.auth.failure-pepper=" + FAILURE_PEPPER,
				"app.auth.access-token-ttl=30m",
				"app.auth.session-ttl=7d",
				"app.auth.refresh-conflict-grace=5s",
				"app.auth.bcrypt-cost=12",
				"app.auth.cookie-secure=true"
		};
	}
}
