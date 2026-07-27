package com.heness.project.account.domain;

public final class AccountRuleViolation extends RuntimeException {

	public AccountRuleViolation(String rule) {
		super(rule);
	}
}
