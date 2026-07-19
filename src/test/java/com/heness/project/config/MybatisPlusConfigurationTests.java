package com.heness.project.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@SpringBootTest
class MybatisPlusConfigurationTests {

	@Autowired
	private MybatisPlusProperties properties;

	@Test
	void 加载Mapper路径配置且不连接MySQL() {
		assertArrayEquals(
				new String[]{"classpath:/mapper/**/*.xml"},
				properties.getMapperLocations()
		);
	}
}
