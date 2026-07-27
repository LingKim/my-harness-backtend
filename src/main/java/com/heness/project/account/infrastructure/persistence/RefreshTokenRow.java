package com.heness.project.account.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("account_refresh_token")
class RefreshTokenRow {
	@TableId(type = IdType.AUTO)
	private Long id;
	private Long sessionId;
	private String tokenHash;
	private String status;
	private LocalDateTime createdAt;
	private LocalDateTime rotatedAt;
	private LocalDateTime expiresAt;

	Long getId() { return id; }
	void setId(Long id) { this.id = id; }
	Long getSessionId() { return sessionId; }
	void setSessionId(Long sessionId) { this.sessionId = sessionId; }
	String getTokenHash() { return tokenHash; }
	void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
	String getStatus() { return status; }
	void setStatus(String status) { this.status = status; }
	LocalDateTime getCreatedAt() { return createdAt; }
	void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
	LocalDateTime getRotatedAt() { return rotatedAt; }
	void setRotatedAt(LocalDateTime rotatedAt) { this.rotatedAt = rotatedAt; }
	LocalDateTime getExpiresAt() { return expiresAt; }
	void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
