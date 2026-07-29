package com.heness.project.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
interface RefreshTokenMapper extends BaseMapper<RefreshTokenRow> {

	RefreshTokenProjection lockByHash(@Param("refreshHash") String refreshHash);

	int markRotated(@Param("tokenId") long tokenId, @Param("rotatedAt") LocalDateTime rotatedAt);

	int revokeActiveForSession(@Param("sessionId") long sessionId);

	int revokeForOtherSessions(
			@Param("accountId") long accountId,
			@Param("currentSessionId") long currentSessionId);
}
