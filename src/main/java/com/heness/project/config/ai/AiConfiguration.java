package com.heness.project.config.ai;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class AiConfiguration {

	@Bean
	ChatModel openAiCompatibleChatModel(AiProperties properties) {
		OpenAiChatOptions options = OpenAiChatOptions.builder()
				.baseUrl(properties.baseUrl())
				.apiKey(properties.apiKey())
				.model(properties.model())
				.build();

		return OpenAiChatModel.builder()
				.options(options)
				.build();
	}
}
