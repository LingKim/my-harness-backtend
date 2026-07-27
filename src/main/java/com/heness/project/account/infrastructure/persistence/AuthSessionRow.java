package com.heness.project.account.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("account_auth_session")
class AuthSessionRow {
	@TableId(type = IdType.AUTO)
	private Long id;
	private String publicId;
	private Long accountId;
	private String accessTokenHash;
	private LocalDateTime accessExpiresAt;
	private LocalDateTime absoluteExpiresAt;
	private String status;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	private LocalDateTime revokedAt;

	Long getId() { return id; }
	void setId(Long id) { this.id = id; }
	String getPublicId() { return publicId; }
	void setPublicId(String publicId) { this.publicId = publicId; }
	Long getAccountId() { return accountId; }
	void setAccountId(Long accountId) { this.accountId = accountId; }
	String getAccessTokenHash() { return accessTokenHash; }
	void setAccessTokenHash(String accessTokenHash) { this.accessTokenHash = accessTokenHash; }
	LocalDateTime getAccessExpiresAt() { return accessExpiresAt; }
	void setAccessExpiresAt(LocalDateTime accessExpiresAt) { this.accessExpiresAt = accessExpiresAt; }
	LocalDateTime getAbsoluteExpiresAt() { return absoluteExpiresAt; }
	void setAbsoluteExpiresAt(LocalDateTime absoluteExpiresAt) { this.absoluteExpiresAt = absoluteExpiresAt; }
	String getStatus() { return status; }
	void setStatus(String status) { this.status = status; }
	LocalDateTime getCreatedAt() { return createdAt; }
	void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
	LocalDateTime getUpdatedAt() { return updatedAt; }
	void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
	LocalDateTime getRevokedAt() { return revokedAt; }
	void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
}
