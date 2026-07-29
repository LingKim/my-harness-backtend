package com.heness.project.account.api;

import com.heness.project.account.application.IssuedAuthentication;
import com.heness.project.account.infrastructure.security.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
final class AuthCookieWriter {

	private final AuthProperties properties;
	private final Clock clock;

	AuthCookieWriter(AuthProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	void writeAuthentication(HttpServletResponse response, IssuedAuthentication authentication) {
		add(response, authenticationCookie(
				AuthCookies.ACCESS,
				authentication.accessToken().rawValue(),
				Duration.between(clock.instant(), authentication.accessExpiresAt())));
		add(response, authenticationCookie(
				AuthCookies.REFRESH,
				authentication.refreshToken().rawValue(),
				Duration.between(clock.instant(), authentication.absoluteExpiresAt())));
	}

	void writeCsrf(HttpServletResponse response, String token) {
		writeCsrf(response, token, clock.instant().plus(properties.sessionTtl()));
	}

	void writeCsrf(HttpServletResponse response, String token, Instant absoluteExpiresAt) {
		ResponseCookie cookie = ResponseCookie.from(AuthCookies.CSRF, token)
				.secure(properties.cookieSecure())
				.httpOnly(false)
				.sameSite("Lax")
				.path("/")
				.maxAge(nonNegative(Duration.between(clock.instant(), absoluteExpiresAt)))
				.build();
		add(response, cookie);
	}

	private Duration nonNegative(Duration duration) {
		return duration.isNegative() ? Duration.ZERO : duration;
	}

	void clear(HttpServletResponse response) {
		add(response, expired(AuthCookies.ACCESS, true));
		add(response, expired(AuthCookies.REFRESH, true));
		add(response, expired(AuthCookies.CSRF, false));
	}

	private ResponseCookie authenticationCookie(String name, String value, Duration maxAge) {
		return ResponseCookie.from(name, value)
				.secure(properties.cookieSecure())
				.httpOnly(true)
				.sameSite("Lax")
				.path("/")
				.maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
				.build();
	}

	private ResponseCookie expired(String name, boolean httpOnly) {
		return ResponseCookie.from(name, "")
				.secure(properties.cookieSecure())
				.httpOnly(httpOnly)
				.sameSite("Lax")
				.path("/")
				.maxAge(Duration.ZERO)
				.build();
	}

	private void add(HttpServletResponse response, ResponseCookie cookie) {
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}
}
