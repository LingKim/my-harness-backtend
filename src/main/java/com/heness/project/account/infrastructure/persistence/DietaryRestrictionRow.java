package com.heness.project.account.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("account_dietary_restriction")
class DietaryRestrictionRow {
	@TableId(type = IdType.AUTO) private Long id;
	private Long accountId;
	private Integer position;
	private String restrictionText;
	private String normalizedText;

	Long getId() { return id; }
	Long getAccountId() { return accountId; }
	void setAccountId(Long value) { accountId = value; }
	Integer getPosition() { return position; }
	void setPosition(Integer value) { position = value; }
	String getRestrictionText() { return restrictionText; }
	void setRestrictionText(String value) { restrictionText = value; }
	String getNormalizedText() { return normalizedText; }
	void setNormalizedText(String value) { normalizedText = value; }
}
