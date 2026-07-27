package com.heness.project.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface AccountMapper extends BaseMapper<AccountRow> {

	@Select("""
			SELECT id, public_id, account_name, normalized_account_name, password_hash, created_at
			FROM account
			WHERE normalized_account_name = #{normalizedName}
			""")
	AccountRow findByNormalizedName(@Param("normalizedName") String normalizedName);
}
