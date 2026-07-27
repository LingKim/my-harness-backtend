package com.heness.project.account.api;

import com.heness.project.account.application.AccountAuthenticationService;
import com.heness.project.account.application.IssuedAuthentication;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Arrays;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
final class AuthSessionController {

	private final AccountAuthenticationService service;
	private final AuthCookieWriter cookies;
	private final ClientIpResolver clientIpResolver;

	AuthSessionController(
			AccountAuthenticationService service,
			AuthCookieWriter cookies,
			ClientIpResolver clientIpResolver) {
		this.service = service;
		this.cookies = cookies;
		this.clientIpResolver = clientIpResolver;
	}

	@PostMapping("/auth-sessions")
	ResponseEntity<AccountResponse> login(
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest servletRequest,
			HttpServletResponse response) {
		IssuedAuthentication authentication = service.login(
				request.accountName(),
				request.password(),
				clientIpResolver.resolve(servletRequest)
		);
		cookies.writeAuthentication(response, authentication);
		return ResponseEntity.created(URI.create("/api/v1/auth-sessions/current"))
				.body(AccountResponse.from(authentication.account()));
	}

	@PostMapping("/auth-sessions:refresh")
	ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
		IssuedAuthentication authentication = service.refresh(cookie(request, AuthCookies.REFRESH));
		cookies.writeAuthentication(response, authentication);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/auth-sessions/current")
	ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		service.logout(cookie(request, AuthCookies.ACCESS), cookie(request, AuthCookies.REFRESH));
		cookies.clear(response);
		return ResponseEntity.noContent().build();
	}

	private String cookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		return Arrays.stream(cookies)
				.filter(cookie -> name.equals(cookie.getName()))
				.map(Cookie::getValue)
				.findFirst()
				.orElse(null);
	}
}
