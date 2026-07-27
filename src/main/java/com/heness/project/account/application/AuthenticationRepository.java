package com.heness.project.account.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthenticationRepository {

	AccountRecord createAccount(
			UUID publicId,
			String accountName,
			String normalizedAccountName,
			String passwordHash,
			Instant createdAt) throws DuplicateAccount;

	Optional<AccountRecord> findAccountByNormalizedName(String normalizedAccountName);

	void createSession(
			AccountRecord account,
			UUID publicSessionId,
			String accessTokenHash,
			String refreshTokenHash,
			Instant accessExpiresAt,
			Instant absoluteExpiresAt,
			Instant createdAt);

	Optional<AccountView> findAccountByActiveAccessToken(String accessTokenHash, Instant now);

	Optional<RefreshTokenRecord> lockRefreshToken(String refreshTokenHash);

	void rotateSession(
			RefreshTokenRecord current,
			String nextAccessTokenHash,
			String nextRefreshTokenHash,
			Instant nextAccessExpiresAt,
			Instant rotatedAt);

	void revokeSession(long sessionId, Instant revokedAt);

	void revokeByTokenDigests(String accessTokenHash, String refreshTokenHash, Instant revokedAt);
}
