package com.heness.project.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
interface TravelContextMapper extends BaseMapper<TravelContextRow> {
	TravelContextRow findByPublicAccountId(@Param("publicId") String publicId);
	int replaceIfVersionMatches(@Param("accountId") long accountId, @Param("expectedVersion") long expectedVersion,
			@Param("countryOrRegion") String countryOrRegion, @Param("city") String city,
			@Param("tripStartDate") LocalDate tripStartDate, @Param("tripEndDate") LocalDate tripEndDate,
			@Param("assistanceNeeds") String assistanceNeeds, @Param("updatedAt") LocalDateTime updatedAt);
	int clearIfVersionMatches(@Param("accountId") long accountId, @Param("expectedVersion") long expectedVersion,
			@Param("updatedAt") LocalDateTime updatedAt);
	Long lockCurrentVersion(@Param("accountId") long accountId);
}
