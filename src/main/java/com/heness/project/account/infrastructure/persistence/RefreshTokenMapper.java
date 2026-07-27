package com.heness.project.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
interface RefreshTokenMapper extends BaseMapper<RefreshTokenRow> {

	@Select("""
			SELECT r.id AS token_id, r.session_id, r.status, r.rotated_at, r.expires_at,
			       s.absolute_expires_at
			FROM account_refresh_token r
			JOIN account_auth_session s ON s.id = r.session_id
			WHERE r.token_hash = #{refreshHash}
			FOR UPDATE
			""")
	RefreshTokenProjection lockByHash(@Param("refreshHash") String refreshHash);

	@Update("""
			UPDATE account_refresh_token
			SET status = 'ROTATED', rotated_at = #{rotatedAt}
			WHERE id = #{tokenId} AND status = 'ACTIVE'
			""")
	int markRotated(@Param("tokenId") long tokenId, @Param("rotatedAt") LocalDateTime rotatedAt);

	@Update("""
			UPDATE account_refresh_token
			SET status = 'REVOKED'
			WHERE session_id = #{sessionId} AND status = 'ACTIVE'
			""")
	int revokeActiveForSession(@Param("sessionId") long sessionId);
}
