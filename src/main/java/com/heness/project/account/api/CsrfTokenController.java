package com.heness.project.account.api;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth-csrf-token")
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
final class CsrfTokenController {
	private final CsrfTokenGenerator generator;
	private final AuthCookieWriter cookies;

	CsrfTokenController(CsrfTokenGenerator generator, AuthCookieWriter cookies) {
		this.generator = generator;
		this.cookies = cookies;
	}

	@GetMapping
	ResponseEntity<Void> issue(HttpServletResponse response) {
		cookies.writeCsrf(response, generator.generate());
		return ResponseEntity.noContent().build();
	}
}
