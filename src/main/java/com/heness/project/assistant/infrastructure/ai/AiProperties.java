package com.heness.project.assistant.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties("app.ai")
public record AiProperties(
		boolean enabled,
		String baseUrl,
		String apiKey,
		String model
) {

	public AiProperties {
		if (enabled && (!StringUtils.hasText(baseUrl)
				|| !StringUtils.hasText(apiKey)
				|| !StringUtils.hasText(model))) {
			throw new IllegalArgumentException(
					"AI 配置不完整：启用 AI 时必须提供 AI_BASE_URL、AI_API_KEY 和 AI_MODEL"
			);
		}
	}
}
