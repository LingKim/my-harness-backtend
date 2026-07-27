package com.heness.project.account.api;

import com.heness.project.account.application.AccountAuthenticationService;
import com.heness.project.account.application.AccountView;
import com.heness.project.account.application.IssuedAuthentication;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/accounts")
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
final class AccountController {

	private final AccountAuthenticationService service;
	private final AuthCookieWriter cookies;

	AccountController(AccountAuthenticationService service, AuthCookieWriter cookies) {
		this.service = service;
		this.cookies = cookies;
	}

	@PostMapping
	ResponseEntity<AccountResponse> register(
			@Valid @RequestBody RegisterAccountRequest request,
			HttpServletResponse response) {
		IssuedAuthentication authentication = service.register(
				request.accountName(),
				request.password(),
				request.confirmPassword(),
				Boolean.TRUE.equals(request.termsAccepted())
		);
		cookies.writeAuthentication(response, authentication);
		return ResponseEntity.created(URI.create("/api/v1/accounts/" + authentication.account().accountId()))
				.body(AccountResponse.from(authentication.account()));
	}

	@GetMapping("/me")
	AccountResponse current(Authentication authentication) {
		return AccountResponse.from((AccountView) authentication.getPrincipal());
	}
}
