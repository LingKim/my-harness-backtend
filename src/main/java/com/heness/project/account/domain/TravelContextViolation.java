package com.heness.project.account.domain;

public final class TravelContextViolation extends RuntimeException {
	public TravelContextViolation(String rule) {
		super(rule);
	}
}
