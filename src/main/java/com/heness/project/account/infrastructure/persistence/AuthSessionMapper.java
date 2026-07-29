package com.heness.project.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
interface AuthSessionMapper extends BaseMapper<AuthSessionRow> {

	AccountAccessProjection findAccountByActiveAccess(
			@Param("accessHash") String accessHash,
			@Param("now") LocalDateTime now);

	int rotateAccess(
			@Param("sessionId") long sessionId,
			@Param("accessHash") String accessHash,
			@Param("accessExpiresAt") LocalDateTime accessExpiresAt,
			@Param("updatedAt") LocalDateTime updatedAt);

	int revoke(@Param("sessionId") long sessionId, @Param("revokedAt") LocalDateTime revokedAt);

	Long findSessionIdByAccessHash(@Param("accessHash") String accessHash);

	Long findSessionIdByRefreshHash(@Param("refreshHash") String refreshHash);

	CurrentSessionProjection lockActiveSession(
			@Param("accountId") long accountId,
			@Param("accessHash") String accessHash,
			@Param("now") LocalDateTime now);

	int revokeOtherSessions(
			@Param("accountId") long accountId,
			@Param("currentSessionId") long currentSessionId,
			@Param("revokedAt") LocalDateTime revokedAt);
}
