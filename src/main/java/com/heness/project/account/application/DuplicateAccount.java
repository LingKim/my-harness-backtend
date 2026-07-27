package com.heness.project.account.application;

public final class DuplicateAccount extends RuntimeException {

	public DuplicateAccount() {
		super("DUPLICATE_ACCOUNT");
	}
}
