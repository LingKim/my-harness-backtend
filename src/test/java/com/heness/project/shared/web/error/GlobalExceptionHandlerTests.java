package com.heness.project.shared.web.error;

import com.heness.project.ProjectApplication;
import com.jayway.jsonpath.JsonPath;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ProjectApplication.class)
@AutoConfigureMockMvc
@Import(GlobalExceptionHandlerTests.ValidationTestController.class)
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 请求体单字段校验失败返回完整ProblemDetail() throws Exception {
		mockMvc.perform(post("/test/validation/body?source=private-value")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "title": "",
						  "cityId": 1,
						  "items": [{"name": "有效名称"}]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("urn:chinamate:problem:validation-failed"))
				.andExpect(jsonPath("$.title").value("请求参数校验失败"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("请检查提交的字段"))
				.andExpect(jsonPath("$.instance").value("/test/validation/body"))
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.traceId").isNotEmpty())
				.andExpect(jsonPath("$.fieldErrors.length()").value(1))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("NotBlank"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("标题不能为空"))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("private-value"))));
	}

	@Test
	void 请求体多个及嵌套字段错误按固定顺序一次返回() throws Exception {
		String request = """
				{
				  "title": "",
				  "cityId": null,
				  "items": [{"name": ""}]
				}
				""";

		MvcResult first = mockMvc.perform(post("/test/validation/body")
					.contentType(MediaType.APPLICATION_JSON)
					.content(request))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.length()").value(3))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("cityId"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("NotNull"))
				.andExpect(jsonPath("$.fieldErrors[1].field").value("items[0].name"))
				.andExpect(jsonPath("$.fieldErrors[1].code").value("NotBlank"))
				.andExpect(jsonPath("$.fieldErrors[2].field").value("title"))
				.andExpect(jsonPath("$.fieldErrors[2].code").value("NotBlank"))
				.andReturn();

		MvcResult second = mockMvc.perform(post("/test/validation/body")
					.contentType(MediaType.APPLICATION_JSON)
					.content(request))
				.andExpect(status().isBadRequest())
				.andReturn();

		Object firstErrors = JsonPath.read(first.getResponse().getContentAsString(), "$.fieldErrors");
		Object secondErrors = JsonPath.read(second.getResponse().getContentAsString(), "$.fieldErrors");
		assertThat(secondErrors).isEqualTo(firstErrors);
	}

	@Test
	void 查询参数违反方法约束时返回字段错误() throws Exception {
		mockMvc.perform(get("/test/validation/query").param("page", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("Min"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("页码必须从 1 开始"));
	}

	@Test
	void 路径参数违反方法约束时返回字段错误() throws Exception {
		mockMvc.perform(get("/test/validation/path/0"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("itemId"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("Min"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("条目标识必须为正数"));
	}

	@Test
	void 缺少必填查询参数时返回字段错误() throws Exception {
		mockMvc.perform(get("/test/validation/query"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("NotNull"));
	}

	@Test
	void 查询参数类型转换失败时不回显原始值() throws Exception {
		mockMvc.perform(get("/test/validation/query").param("page", "sensitive-value"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("page"))
				.andExpect(jsonPath("$.fieldErrors[0].code").value("typeMismatch"))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("sensitive-value"))));
	}

	@Test
	void 非法Json返回安全的格式错误() throws Exception {
		mockMvc.perform(post("/test/validation/body")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"sensitive-value\""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("urn:chinamate:problem:malformed-request"))
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist())
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("sensitive-value"))))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("HttpMessageNotReadableException"))));
	}

	@Test
	void 未知异常返回安全500并使用同一traceId记录日志(CapturedOutput output) throws Exception {
		MvcResult result = mockMvc.perform(get("/test/validation/explode"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("urn:chinamate:problem:internal-server-error"))
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.detail").value("服务器暂时无法处理请求"))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("sensitive-value"))))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("IllegalStateException"))))
				.andReturn();

		String body = result.getResponse().getContentAsString();
		String traceId = JsonPath.read(body, "$.traceId");
		assertThat(output).contains("traceId=" + traceId);
		assertThat(output).contains("exceptionType=java.lang.IllegalStateException");
		assertThat(output).doesNotContain("sensitive-value");
	}

	@RestController
	@RequestMapping("/test/validation")
	static class ValidationTestController {

		@PostMapping("/body")
		ValidationRequest validateBody(@Valid @RequestBody ValidationRequest request) {
			return request;
		}

		@GetMapping("/query")
		int validateQuery(
				@RequestParam(name = "page")
				@Min(value = 1, message = "页码必须从 1 开始") int requestedPage) {
			return requestedPage;
		}

		@GetMapping("/path/{itemId}")
		long validatePath(
				@PathVariable(name = "itemId")
				@Min(value = 1, message = "条目标识必须为正数") long requestedItemId) {
			return requestedItemId;
		}

		@GetMapping("/explode")
		void explode() {
			throw new IllegalStateException("SQL password=sensitive-value");
		}
	}

	record ValidationRequest(
			@NotBlank(message = "标题不能为空") String title,
			@NotNull(message = "城市不能为空") Long cityId,
			List<@Valid ItemRequest> items) {
	}

	record ItemRequest(@NotBlank(message = "条目名称不能为空") String name) {
	}
}
