package com.heness.project.account.domain;

public enum PreferredLanguage {
	ZH_CN("zh-CN"),
	EN("en");

	private final String wireValue;

	PreferredLanguage(String wireValue) {
		this.wireValue = wireValue;
	}

	public String wireValue() {
		return wireValue;
	}

	public static PreferredLanguage fromWireValue(String value) {
		for (PreferredLanguage language : values()) {
			if (language.wireValue.equals(value)) {
				return language;
			}
		}
		throw new AccountRuleViolation("PREFERRED_LANGUAGE_INVALID");
	}
}
