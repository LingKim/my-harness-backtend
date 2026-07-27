package com.heness.project.account.application;

public interface FailureKeyService {

	String accountKey(String normalizedAccountName);

	String ipKey(String clientIp);
}
