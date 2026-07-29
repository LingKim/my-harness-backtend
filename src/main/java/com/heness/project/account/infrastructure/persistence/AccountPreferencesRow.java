package com.heness.project.account.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("account_preferences")
class AccountPreferencesRow {
	@TableId
	private Long accountId;
	private String preferredLanguage;
	private LocalDateTime updatedAt;

	Long getAccountId() { return accountId; }
	void setAccountId(Long accountId) { this.accountId = accountId; }
	String getPreferredLanguage() { return preferredLanguage; }
	void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }
	LocalDateTime getUpdatedAt() { return updatedAt; }
	void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
