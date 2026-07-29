package com.heness.project.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
interface AccountMapper extends BaseMapper<AccountRow> {

	AccountRow findByNormalizedName(@Param("normalizedName") String normalizedName);

	AccountRow lockByPublicId(@Param("publicId") String publicId);

	int updatePasswordHash(
			@Param("accountId") long accountId,
			@Param("passwordHash") String passwordHash);
}
