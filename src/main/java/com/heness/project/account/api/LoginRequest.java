package com.heness.project.account.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
		@NotBlank
		@Pattern(regexp = "[A-Za-z0-9_]{4,20}")
		String accountName,
		@NotBlank
		String password) {
}
