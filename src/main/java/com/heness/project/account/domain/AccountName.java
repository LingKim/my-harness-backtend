package com.heness.project.account.domain;

import java.util.Locale;
import java.util.regex.Pattern;

public final class AccountName {

	private static final Pattern VALID_ACCOUNT_NAME = Pattern.compile("[A-Za-z0-9_]{4,20}");

	private final String displayValue;
	private final String normalizedValue;

	private AccountName(String displayValue) {
		this.displayValue = displayValue;
		this.normalizedValue = displayValue.toLowerCase(Locale.ROOT);
	}

	public static AccountName of(String value) {
		if (value == null || !VALID_ACCOUNT_NAME.matcher(value).matches()) {
			throw new AccountRuleViolation("ACCOUNT_NAME_INVALID");
		}
		return new AccountName(value);
	}

	public String displayValue() {
		return displayValue;
	}

	public String normalizedValue() {
		return normalizedValue;
	}

	@Override
	public String toString() {
		return "AccountName[redacted]";
	}
}
