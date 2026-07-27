package com.heness.project.account.domain;

import java.util.regex.Pattern;

public final class PasswordPolicy {

	private static final Pattern ASCII_PASSWORD = Pattern.compile("[A-Za-z0-9!\"#$%&'()*+,./:;<=>?@\\[\\]^_`{|}~-]{8,64}");
	private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
	private static final Pattern HAS_DIGIT = Pattern.compile(".*[0-9].*");

	private PasswordPolicy() {
	}

	public static boolean isValid(String password) {
		return password != null
				&& ASCII_PASSWORD.matcher(password).matches()
				&& HAS_LETTER.matcher(password).matches()
				&& HAS_DIGIT.matcher(password).matches();
	}

	public static void requireValid(String password) {
		if (!isValid(password)) {
			throw new AccountRuleViolation("PASSWORD_INVALID");
		}
	}
}
