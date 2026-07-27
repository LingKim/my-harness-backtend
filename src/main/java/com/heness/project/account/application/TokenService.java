package com.heness.project.account.application;

public interface TokenService {

	SecretToken issue();

	String digest(String rawToken);
}
