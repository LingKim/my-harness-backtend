package com.heness.project.account.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

	@Bean
	PasswordEncoder accountPasswordEncoder(AuthProperties properties) {
		return new BCryptPasswordEncoder(properties.bcryptCost());
	}

	@Bean
	Clock accountClock() {
		return Clock.systemUTC();
	}
}
