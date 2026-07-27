package com.heness.project.account.infrastructure.persistence;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
interface LoginFailureMapper {

	@Insert("""
			INSERT IGNORE INTO account_auth_failure_bucket
			    (key_type, key_hash, window_started_at, failure_count, blocked_until, updated_at)
			VALUES
			    (#{keyType}, #{keyHash}, #{now}, 0, NULL, #{now})
			""")
	int ensureExists(
			@Param("keyType") String keyType,
			@Param("keyHash") String keyHash,
			@Param("now") LocalDateTime now);

	@Select("""
			SELECT id, key_type, key_hash, window_started_at, failure_count, blocked_until, updated_at
			FROM account_auth_failure_bucket
			WHERE key_type = #{keyType} AND key_hash = #{keyHash}
			FOR UPDATE
			""")
	LoginFailureRow lock(
			@Param("keyType") String keyType,
			@Param("keyHash") String keyHash);

	@Select("""
			SELECT id, key_type, key_hash, window_started_at, failure_count, blocked_until, updated_at
			FROM account_auth_failure_bucket
			WHERE key_type = #{keyType} AND key_hash = #{keyHash}
			""")
	LoginFailureRow find(
			@Param("keyType") String keyType,
			@Param("keyHash") String keyHash);

	@Update("""
			UPDATE account_auth_failure_bucket
			SET window_started_at = #{windowStartedAt},
			    failure_count = #{failureCount},
			    blocked_until = #{blockedUntil},
			    updated_at = #{updatedAt}
			WHERE id = #{id}
			""")
	int update(
			@Param("id") long id,
			@Param("windowStartedAt") LocalDateTime windowStartedAt,
			@Param("failureCount") int failureCount,
			@Param("blockedUntil") LocalDateTime blockedUntil,
			@Param("updatedAt") LocalDateTime updatedAt);

	@Delete("DELETE FROM account_auth_failure_bucket WHERE key_type = 'ACCOUNT' AND key_hash = #{keyHash}")
	int deleteAccount(@Param("keyHash") String keyHash);
}
