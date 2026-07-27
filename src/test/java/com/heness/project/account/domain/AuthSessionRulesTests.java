package com.heness.project.account.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSessionRulesTests {

	private static final Instant CREATED_AT = Instant.parse("2026-07-27T08:00:00Z");

	@Test
	void 会话固定短Token三十分钟和绝对七天期限() {
		AuthSessionWindow window = AuthSessionWindow.start(CREATED_AT);

		assertThat(window.accessExpiresAt()).isEqualTo(CREATED_AT.plus(Duration.ofMinutes(30)));
		assertThat(window.absoluteExpiresAt()).isEqualTo(CREATED_AT.plus(Duration.ofDays(7)));
	}

	@Test
	void 刷新只更新短Token期限且不滑动绝对期限() {
		AuthSessionWindow original = AuthSessionWindow.start(CREATED_AT);
		Instant refreshedAt = CREATED_AT.plus(Duration.ofMinutes(20));

		AuthSessionWindow refreshed = original.refresh(refreshedAt);

		assertThat(refreshed.accessExpiresAt()).isEqualTo(refreshedAt.plus(Duration.ofMinutes(30)));
		assertThat(refreshed.absoluteExpiresAt()).isEqualTo(original.absoluteExpiresAt());
	}

	@Test
	void 已轮换长Token五秒内是并发冲突之后是重放() {
		Instant rotatedAt = CREATED_AT.plusSeconds(60);

		assertThat(RefreshTokenUse.classifyRotated(rotatedAt, rotatedAt.plusSeconds(5)))
				.isEqualTo(RefreshTokenUse.CONCURRENT_CONFLICT);
		assertThat(RefreshTokenUse.classifyRotated(rotatedAt, rotatedAt.plusSeconds(5).plusNanos(1)))
				.isEqualTo(RefreshTokenUse.REPLAY);
	}
}
