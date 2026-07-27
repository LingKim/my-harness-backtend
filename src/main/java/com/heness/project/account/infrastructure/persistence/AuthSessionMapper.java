package com.heness.project.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
interface AuthSessionMapper extends BaseMapper<AuthSessionRow> {

	@Select("""
			SELECT a.public_id, a.account_name, a.created_at
			FROM account_auth_session s
			JOIN account a ON a.id = s.account_id
			WHERE s.access_token_hash = #{accessHash}
			  AND s.status = 'ACTIVE'
			  AND s.access_expires_at > #{now}
			  AND s.absolute_expires_at > #{now}
			""")
	AccountAccessProjection findAccountByActiveAccess(
			@Param("accessHash") String accessHash,
			@Param("now") LocalDateTime now);

	@Update("""
			UPDATE account_auth_session
			SET access_token_hash = #{accessHash},
			    access_expires_at = #{accessExpiresAt},
			    updated_at = #{updatedAt}
			WHERE id = #{sessionId} AND status = 'ACTIVE'
			""")
	int rotateAccess(
			@Param("sessionId") long sessionId,
			@Param("accessHash") String accessHash,
			@Param("accessExpiresAt") LocalDateTime accessExpiresAt,
			@Param("updatedAt") LocalDateTime updatedAt);

	@Update("""
			UPDATE account_auth_session
			SET status = 'REVOKED', revoked_at = #{revokedAt}, updated_at = #{revokedAt}
			WHERE id = #{sessionId} AND status = 'ACTIVE'
			""")
	int revoke(@Param("sessionId") long sessionId, @Param("revokedAt") LocalDateTime revokedAt);

	@Select("SELECT id FROM account_auth_session WHERE access_token_hash = #{accessHash}")
	Long findSessionIdByAccessHash(@Param("accessHash") String accessHash);

	@Select("""
			SELECT s.id
			FROM account_auth_session s
			JOIN account_refresh_token r ON r.session_id = s.id
			WHERE r.token_hash = #{refreshHash}
			""")
	Long findSessionIdByRefreshHash(@Param("refreshHash") String refreshHash);
}
