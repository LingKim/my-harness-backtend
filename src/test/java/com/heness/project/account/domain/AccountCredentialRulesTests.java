package com.heness.project.account.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountCredentialRulesTests {

	@Test
	void 账号保留显示值并使用ASCII小写值比较唯一性() {
		AccountName accountName = AccountName.of("China_2026");

		assertThat(accountName.displayValue()).isEqualTo("China_2026");
		assertThat(accountName.normalizedValue()).isEqualTo("china_2026");
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "abcdefghijklmnopqrstu", "旅行者", "abc-def", "abc def"})
	void 拒绝不符合四到二十位ASCII规则的账号(String value) {
		assertThatThrownBy(() -> AccountName.of(value))
				.isInstanceOf(AccountRuleViolation.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc12345", "Travel_2026", "1234abcd5678"})
	void 接受同时包含字母和数字的八到六十四位密码(String value) {
		assertThat(PasswordPolicy.isValid(value)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"short1", "onlyletters", "12345678", "旅行abc123", "abc 1234"})
	void 拒绝长度字符集或组合不符合规则的密码(String value) {
		assertThat(PasswordPolicy.isValid(value)).isFalse();
	}
}
