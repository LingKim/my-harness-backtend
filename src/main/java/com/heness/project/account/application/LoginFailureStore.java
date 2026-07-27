package com.heness.project.account.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface LoginFailureStore {

	Optional<Duration> blockedFor(String accountKey, String ipKey, Instant now);

	Optional<Duration> recordFailure(String accountKey, String ipKey, Instant now);

	void clearAccountFailures(String accountKey);
}
