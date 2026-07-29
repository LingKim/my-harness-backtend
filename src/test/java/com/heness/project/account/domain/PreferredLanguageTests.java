package com.heness.project.account.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreferredLanguageTests {
	@Test
	void onlySupportsChineseAndEnglish() {
		assertThat(PreferredLanguage.fromWireValue("zh-CN")).isEqualTo(PreferredLanguage.ZH_CN);
		assertThat(PreferredLanguage.fromWireValue("en")).isEqualTo(PreferredLanguage.EN);
		assertThatThrownBy(() -> PreferredLanguage.fromWireValue("zh-TW"))
				.isInstanceOf(AccountRuleViolation.class);
	}
}
