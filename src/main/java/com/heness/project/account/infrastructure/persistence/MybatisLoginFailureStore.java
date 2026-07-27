package com.heness.project.account.infrastructure.persistence;

import com.heness.project.account.application.LoginFailureStore;
import com.heness.project.account.domain.LoginFailureBucket;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
class MybatisLoginFailureStore implements LoginFailureStore {

	private static final int ACCOUNT_THRESHOLD = 5;
	private static final int IP_THRESHOLD = 20;

	private final LoginFailureMapper mapper;

	MybatisLoginFailureStore(LoginFailureMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	public Optional<Duration> blockedFor(String accountKey, String ipKey, Instant now) {
		return maximum(blockedDuration(mapper.find("ACCOUNT", accountKey), now),
				blockedDuration(mapper.find("IP", ipKey), now));
	}

	@Override
	public Optional<Duration> recordFailure(String accountKey, String ipKey, Instant now) {
		Optional<Duration> accountBlocked = record("ACCOUNT", accountKey, ACCOUNT_THRESHOLD, now);
		Optional<Duration> ipBlocked = record("IP", ipKey, IP_THRESHOLD, now);
		return maximum(accountBlocked, ipBlocked);
	}

	@Override
	public void clearAccountFailures(String accountKey) {
		mapper.deleteAccount(accountKey);
	}

	private Optional<Duration> record(String keyType, String keyHash, int threshold, Instant now) {
		mapper.ensureExists(keyType, keyHash, DatabaseTimes.toDatabase(now));
		LoginFailureRow row = mapper.lock(keyType, keyHash);
		LoginFailureBucket current = new LoginFailureBucket(
				DatabaseTimes.toInstant(row.windowStartedAt()),
				row.failureCount(),
				DatabaseTimes.toInstant(row.blockedUntil())
		);
		LoginFailureBucket next = current.recordFailure(now, threshold);
		mapper.update(
				row.id(),
				DatabaseTimes.toDatabase(next.windowStartedAt().orElseThrow()),
				next.failureCount(),
				DatabaseTimes.toDatabase(next.blockedUntil().orElse(null)),
				DatabaseTimes.toDatabase(now)
		);
		return blockedDuration(next, now);
	}

	private Optional<Duration> blockedDuration(LoginFailureRow row, Instant now) {
		if (row == null) {
			return Optional.empty();
		}
		return blockedDuration(new LoginFailureBucket(
				DatabaseTimes.toInstant(row.windowStartedAt()),
				row.failureCount(),
				DatabaseTimes.toInstant(row.blockedUntil())
		), now);
	}

	private Optional<Duration> blockedDuration(LoginFailureBucket bucket, Instant now) {
		return bucket.blockedUntil()
				.filter(until -> now.isBefore(until))
				.map(until -> Duration.between(now, until));
	}

	@SafeVarargs
	private final Optional<Duration> maximum(Optional<Duration>... durations) {
		return Stream.of(durations)
				.flatMap(Optional::stream)
				.max(Comparator.naturalOrder());
	}
}
