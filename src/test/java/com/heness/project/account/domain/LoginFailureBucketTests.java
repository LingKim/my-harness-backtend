package com.heness.project.account.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LoginFailureBucketTests {

	private static final Instant START = Instant.parse("2026-07-27T08:00:00Z");

	@Test
	void 账号第五次失败立即限制十五分钟() {
		LoginFailureBucket bucket = LoginFailureBucket.empty();
		for (int failure = 1; failure <= 4; failure++) {
			bucket = bucket.recordFailure(START.plusSeconds(failure), 5);
			assertThat(bucket.isBlockedAt(START.plusSeconds(failure))).isFalse();
		}

		bucket = bucket.recordFailure(START.plusSeconds(5), 5);

		assertThat(bucket.failureCount()).isEqualTo(5);
		assertThat(bucket.blockedUntil()).contains(START.plusSeconds(5).plus(Duration.ofMinutes(15)));
	}

	@Test
	void 十五分钟窗口结束后从一次失败重新开始() {
		LoginFailureBucket bucket = LoginFailureBucket.empty()
				.recordFailure(START, 5)
				.recordFailure(START.plus(Duration.ofMinutes(15)), 5);

		assertThat(bucket.failureCount()).isEqualTo(1);
		assertThat(bucket.windowStartedAt()).contains(START.plus(Duration.ofMinutes(15)));
	}

	@Test
	void 到达blockedUntil即解除限制() {
		LoginFailureBucket bucket = LoginFailureBucket.empty();
		for (int failure = 0; failure < 5; failure++) {
			bucket = bucket.recordFailure(START.plusSeconds(failure), 5);
		}
		Instant blockedUntil = bucket.blockedUntil().orElseThrow();

		assertThat(bucket.isBlockedAt(blockedUntil.minusNanos(1))).isTrue();
		assertThat(bucket.isBlockedAt(blockedUntil)).isFalse();
	}
}
