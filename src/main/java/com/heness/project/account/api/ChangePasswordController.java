package com.heness.project.account.api;

import com.heness.project.account.application.AccountAuthenticationService;
import com.heness.project.account.application.AccountView;
import com.heness.project.account.application.ChangedPasswordAuthentication;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
final class ChangePasswordController {
	private final AccountAuthenticationService service;
	private final AuthCookieWriter cookies;

	ChangePasswordController(AccountAuthenticationService service, AuthCookieWriter cookies) {
		this.service = service;
		this.cookies = cookies;
	}

	@PostMapping("/api/v1/accounts/me:change-password")
	ResponseEntity<Void> changePassword(
			Authentication authentication,
			@RequestBody ChangePasswordRequest request,
			HttpServletRequest servletRequest,
			HttpServletResponse response) {
		AccountView account = (AccountView) authentication.getPrincipal();
		ChangedPasswordAuthentication changed = service.changePassword(
				account.accountId(), cookie(servletRequest, AuthCookies.ACCESS), request.currentPassword(),
				request.newPassword(), request.confirmNewPassword());
		cookies.writeAuthentication(response, changed.authentication());
		cookies.writeCsrf(response, changed.csrfToken().rawValue(), changed.authentication().absoluteExpiresAt());
		return ResponseEntity.noContent().build();
	}

	private String cookie(HttpServletRequest request, String name) {
		return request.getCookies() == null ? null : Arrays.stream(request.getCookies())
				.filter(candidate -> name.equals(candidate.getName()))
				.map(Cookie::getValue).findFirst().orElse(null);
	}
}
