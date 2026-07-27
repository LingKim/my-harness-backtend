package com.heness.project.account.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("account")
class AccountRow {

	@TableId(type = IdType.AUTO)
	private Long id;
	private String publicId;
	private String accountName;
	private String normalizedAccountName;
	private String passwordHash;
	private LocalDateTime createdAt;

	Long getId() { return id; }
	void setId(Long id) { this.id = id; }
	String getPublicId() { return publicId; }
	void setPublicId(String publicId) { this.publicId = publicId; }
	String getAccountName() { return accountName; }
	void setAccountName(String accountName) { this.accountName = accountName; }
	String getNormalizedAccountName() { return normalizedAccountName; }
	void setNormalizedAccountName(String normalizedAccountName) { this.normalizedAccountName = normalizedAccountName; }
	String getPasswordHash() { return passwordHash; }
	void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
	LocalDateTime getCreatedAt() { return createdAt; }
	void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
