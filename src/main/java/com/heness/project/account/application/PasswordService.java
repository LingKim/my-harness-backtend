package com.heness.project.account.application;

public interface PasswordService {

	String encode(String rawPassword);

	boolean matchesOrDummy(String rawPassword, String encodedPassword);
}
