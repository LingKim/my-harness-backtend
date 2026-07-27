package com.heness.project.account.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
public final class AuthCsrfFilter extends OncePerRequestFilter {

	private static final Set<String> PROTECTED_PATHS = Set.of(
			"/api/v1/accounts",
			"/api/v1/auth-sessions",
			"/api/v1/auth-sessions:refresh",
			"/api/v1/auth-sessions/current"
	);

	private final String allowedOrigin;

	public AuthCsrfFilter(@Value("${app.cors.allowed-origin}") String allowedOrigin) {
		this.allowedOrigin = allowedOrigin;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return "GET".equals(request.getMethod())
				|| "HEAD".equals(request.getMethod())
				|| "OPTIONS".equals(request.getMethod())
				|| !PROTECTED_PATHS.contains(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String cookie = cookie(request, AuthCookies.CSRF);
		String header = request.getHeader(AuthCookies.CSRF_HEADER);
		String origin = request.getHeader("Origin");
		if (!allowedOrigin.equals(origin) || !constantTimeEquals(cookie, header)) {
			writeForbidden(response);
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean constantTimeEquals(String first, String second) {
		return first != null && second != null && MessageDigest.isEqual(
				first.getBytes(StandardCharsets.UTF_8),
				second.getBytes(StandardCharsets.UTF_8));
	}

	private String cookie(HttpServletRequest request, String name) {
		return request.getCookies() == null ? null : Arrays.stream(request.getCookies())
				.filter(candidate -> name.equals(candidate.getName()))
				.map(Cookie::getValue)
				.findFirst()
				.orElse(null);
	}

	private void writeForbidden(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter().write("""
				{"type":"urn:chinamate:problem:csrf-invalid","title":"请求安全校验失败","status":403,"detail":"请刷新页面后重试","code":"CSRF_INVALID","traceId":"%s"}
				""".formatted(UUID.randomUUID()));
	}
}
