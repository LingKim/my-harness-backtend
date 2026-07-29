package com.heness.project.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
interface DietaryRestrictionMapper extends BaseMapper<DietaryRestrictionRow> {
	List<DietaryRestrictionRow> findOrdered(@Param("accountId") long accountId);
	int deleteForAccount(@Param("accountId") long accountId);
}
