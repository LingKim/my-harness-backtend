package com.heness.project.account.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterAccountRequest(
		@NotBlank
		@Pattern(regexp = "[A-Za-z0-9_]{4,20}")
		String accountName,
		@NotBlank
		@Size(min = 8, max = 64)
		@Pattern(regexp = "(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]+")
		String password,
		@NotBlank
		String confirmPassword,
		@NotNull
		@AssertTrue
		Boolean termsAccepted) {
}
