package com.heness.project.account.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public record LoginFailureBucket(
		Instant windowStartedAtValue,
		int failureCount,
		Instant blockedUntilValue) {

	private static final Duration WINDOW = Duration.ofMinutes(15);
	private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

	public static LoginFailureBucket empty() {
		return new LoginFailureBucket(null, 0, null);
	}

	public LoginFailureBucket recordFailure(Instant now, int threshold) {
		if (threshold < 1) {
			throw new IllegalArgumentException("threshold 必须大于零");
		}
		if (isBlockedAt(now)) {
			return this;
		}

		boolean newWindow = windowStartedAtValue == null
				|| !now.isBefore(windowStartedAtValue.plus(WINDOW));
		Instant nextWindow = newWindow ? now : windowStartedAtValue;
		int nextCount = newWindow ? 1 : failureCount + 1;
		Instant nextBlockedUntil = nextCount >= threshold ? now.plus(BLOCK_DURATION) : null;
		return new LoginFailureBucket(nextWindow, nextCount, nextBlockedUntil);
	}

	public boolean isBlockedAt(Instant now) {
		return blockedUntilValue != null && now.isBefore(blockedUntilValue);
	}

	public Optional<Instant> windowStartedAt() {
		return Optional.ofNullable(windowStartedAtValue);
	}

	public Optional<Instant> blockedUntil() {
		return Optional.ofNullable(blockedUntilValue);
	}
}
