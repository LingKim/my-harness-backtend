package com.heness.project.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
interface AccountPreferencesMapper extends BaseMapper<AccountPreferencesRow> {
	int updateLanguage(@Param("publicId") String publicId, @Param("language") String language,
			@Param("updatedAt") LocalDateTime updatedAt);
	AccountAccessProjection findAccountView(@Param("publicId") String publicId);
}
