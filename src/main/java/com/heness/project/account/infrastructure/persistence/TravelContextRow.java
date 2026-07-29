package com.heness.project.account.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("account_travel_context")
class TravelContextRow {
	@TableId private Long accountId;
	private String countryOrRegion;
	private String city;
	private LocalDate tripStartDate;
	private LocalDate tripEndDate;
	private String assistanceNeeds;
	private Long version;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	Long getAccountId() { return accountId; }
	void setAccountId(Long value) { accountId = value; }
	String getCountryOrRegion() { return countryOrRegion; }
	void setCountryOrRegion(String value) { countryOrRegion = value; }
	String getCity() { return city; }
	void setCity(String value) { city = value; }
	LocalDate getTripStartDate() { return tripStartDate; }
	void setTripStartDate(LocalDate value) { tripStartDate = value; }
	LocalDate getTripEndDate() { return tripEndDate; }
	void setTripEndDate(LocalDate value) { tripEndDate = value; }
	String getAssistanceNeeds() { return assistanceNeeds; }
	void setAssistanceNeeds(String value) { assistanceNeeds = value; }
	Long getVersion() { return version; }
	void setVersion(Long value) { version = value; }
	LocalDateTime getCreatedAt() { return createdAt; }
	void setCreatedAt(LocalDateTime value) { createdAt = value; }
	LocalDateTime getUpdatedAt() { return updatedAt; }
	void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
