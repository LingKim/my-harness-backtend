package com.heness.project.account.api;

import com.heness.project.account.application.AccountPreferencesService;
import com.heness.project.account.application.AccountView;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.heness.project.account.domain.TravelContextViolation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts/me")
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
final class AccountPreferencesController {
	private final AccountPreferencesService service;

	AccountPreferencesController(AccountPreferencesService service) {
		this.service = service;
	}

	@GetMapping("/travel-context")
	TravelContextResponse getTravelContext(Authentication authentication) {
		return TravelContextResponse.from(service.getTravelContext(principal(authentication).accountId()));
	}

	@PutMapping("/travel-context")
	TravelContextResponse replaceTravelContext(
			Authentication authentication, @Valid @RequestBody TravelContextRequest request) {
		return TravelContextResponse.from(service.replaceTravelContext(
				principal(authentication).accountId(), request.countryOrRegion(), request.city(),
				request.tripStartDate(), request.tripEndDate(), request.dietaryRestrictions(),
				request.assistanceNeeds(), request.version()));
	}

	@DeleteMapping("/travel-context")
	ResponseEntity<Void> clearTravelContext(
			Authentication authentication, @RequestParam long version,
			HttpServletRequest request) {
		if (version < 0) {
			throw new TravelContextViolation("VERSION_INVALID");
		}
		if (hasRequestBody(request)) {
			throw new TravelContextViolation("DELETE_BODY_NOT_ALLOWED");
		}
		service.clearTravelContext(principal(authentication).accountId(), version);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping
	AccountResponse updateAccount(Authentication authentication, @Valid @RequestBody UpdateAccountRequest request) {
		return AccountResponse.from(service.updatePreferredLanguage(principal(authentication).accountId(),
				request.preferredLanguage()));
	}

	private AccountView principal(Authentication authentication) {
		return (AccountView) authentication.getPrincipal();
	}

	private boolean hasRequestBody(HttpServletRequest request) {
		long contentLength = request.getContentLengthLong();
		if (contentLength > 0) {
			return true;
		}
		if (request.getHeader("Transfer-Encoding") != null) {
			return true;
		}
		return contentLength < 0 && request.getContentType() != null;
	}
}
