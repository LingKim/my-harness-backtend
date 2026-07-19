package com.heness.project;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ProjectApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
		assertTrue(
				applicationContext.getBeansOfType(ChatModel.class).isEmpty(),
				"默认配置不得创建会调用外部模型的 ChatModel"
		);
	}

}
