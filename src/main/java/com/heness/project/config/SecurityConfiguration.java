package com.heness.project.config;

import com.heness.project.account.api.AccessTokenAuthenticationFilter;
import com.heness.project.account.api.AuthCsrfFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain applicationSecurityFilterChain(
			HttpSecurity http,
			@Value("${app.auth.enabled:false}") boolean authEnabled,
			ObjectProvider<AccessTokenAuthenticationFilter> accessFilterProvider,
			ObjectProvider<AuthCsrfFilter> csrfFilterProvider) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.disable())
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
					response.getWriter().write("""
							{"type":"urn:chinamate:problem:authentication-required","title":"需要登录","status":401,"detail":"当前登录状态无效或已过期","code":"AUTHENTICATION_REQUIRED","traceId":"%s"}
							""".formatted(UUID.randomUUID()));
				}));

		if (authEnabled) {
			http.authorizeHttpRequests(authorize -> authorize
					.requestMatchers("/api/v1/accounts/me", "/api/v1/accounts/me/**", "/api/v1/accounts/me:change-password").authenticated()
					.anyRequest().permitAll());
			AuthCsrfFilter csrfFilter = csrfFilterProvider.getIfAvailable();
			AccessTokenAuthenticationFilter accessFilter = accessFilterProvider.getIfAvailable();
			if (csrfFilter != null) {
				http.addFilterBefore(csrfFilter, UsernamePasswordAuthenticationFilter.class);
			}
			if (accessFilter != null) {
				http.addFilterBefore(accessFilter, UsernamePasswordAuthenticationFilter.class);
			}
		} else {
			http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
		}
		return http.build();
	}
}
