package com.heness.project.assistant.infrastructure.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(AiConfiguration.class);

	@Test
	void 默认关闭时不创建模型() {
		contextRunner
				.withPropertyValues("app.ai.enabled=false")
				.run(context -> {
					assertTrue(context.isRunning());
					assertTrue(context.getBeansOfType(ChatModel.class).isEmpty());
				});
	}

	@Test
	void 启用但缺少必要配置时启动失败() {
		contextRunner
				.withPropertyValues(
						"app.ai.enabled=true",
						"app.ai.base-url=",
						"app.ai.api-key=",
						"app.ai.model="
				)
				.run(context -> {
					assertNotNull(context.getStartupFailure());
					assertTrue(rootCauseMessage(context.getStartupFailure()).contains("AI 配置不完整"));
				});
	}

	@Test
	void 配置完整时创建模型但不发起请求() {
		contextRunner
				.withPropertyValues(
						"app.ai.enabled=true",
						"app.ai.base-url=https://example.invalid",
						"app.ai.api-key=test-only-key",
						"app.ai.model=test-model"
				)
				.run(context -> {
					assertTrue(context.isRunning());
					assertTrue(context.getBeansOfType(ChatModel.class).size() == 1);
				});
	}

	private String rootCauseMessage(Throwable failure) {
		Throwable rootCause = failure;
		while (rootCause.getCause() != null) {
			rootCause = rootCause.getCause();
		}
		return rootCause.getMessage() == null ? "" : rootCause.getMessage();
	}
}
