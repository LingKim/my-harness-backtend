package com.heness.project.account.api;

import com.heness.project.account.application.AccountAuthenticationService;
import com.heness.project.account.application.AccountPreferencesService;
import com.heness.project.account.application.AccountView;
import com.heness.project.account.application.AuthFailure;
import com.heness.project.account.application.AuthFailureReason;
import com.heness.project.account.application.TravelContextVersionConflict;
import com.heness.project.account.domain.TravelContext;
import com.heness.project.account.infrastructure.security.AuthProperties;
import com.heness.project.shared.web.error.ApiProblemDetailFactory;
import com.heness.project.shared.web.error.GlobalExceptionHandler;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class AccountPreferencesApiTests {
	private static final String ORIGIN = "http://localhost:3000";
	private static final String CSRF = "csrf-value";
	private static final Instant NOW = Instant.parse("2026-07-29T08:00:00Z");
	private static final AccountView ACCOUNT = new AccountView(
			UUID.fromString("78cd6aa7-7bc0-4d71-b1ab-e26f4fdf570f"), "China_2026", NOW, "zh-CN");

	private AccountPreferencesService preferences;
	private AccountAuthenticationService authentication;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		preferences = mock(AccountPreferencesService.class);
		authentication = mock(AccountAuthenticationService.class);
		AuthProperties properties = new AuthProperties(true, "test-token-pepper-32-characters-minimum",
				"test-failure-pepper-32-characters-minimum", Duration.ofMinutes(30), Duration.ofDays(7),
				Duration.ofSeconds(5), 12, true, List.of());
		AuthCookieWriter writer = new AuthCookieWriter(properties, Clock.fixed(NOW, ZoneOffset.UTC));
		com.fasterxml.jackson.databind.ObjectMapper objectMapper = JsonMapper.builder()
				.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
		objectMapper.addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);
		MappingJackson2HttpMessageConverter json = new MappingJackson2HttpMessageConverter(objectMapper);
		AccountAuthExceptionHandler authAdvice = new AccountAuthExceptionHandler(writer);
		mockMvc = MockMvcBuilders.standaloneSetup(
				new AccountPreferencesController(preferences), new ChangePasswordController(authentication, writer))
				.setMessageConverters(json)
				.setControllerAdvice(new AccountPreferencesExceptionHandler(authAdvice), authAdvice,
						new GlobalExceptionHandler(new ApiProblemDetailFactory()))
				.addFilters(chunkedRequestMetadata(), new AuthCsrfFilter(ORIGIN)).build();
	}

	@Test
	void getReturnsStableEmptyRepresentationFromAuthenticatedPrincipal() throws Exception {
		when(preferences.getTravelContext(ACCOUNT.accountId())).thenReturn(TravelContext.empty(0));
		mockMvc.perform(get("/api/v1/accounts/me/travel-context").principal(principal()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.dietaryRestrictions").isArray())
				.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void putNormalizesAndReturnsUpdatedRepresentation() throws Exception {
		when(preferences.replaceTravelContext(any(), any(), any(), any(), any(), any(), any(), anyLong()))
				.thenReturn(TravelContext.replace("China", "Shanghai", null, null,
						List.of("Vegan"), null, 1));
		mockMvc.perform(put("/api/v1/accounts/me/travel-context").principal(principal()).with(request -> {
				request.setCookies(new Cookie(AuthCookies.CSRF, CSRF)); return request; })
				.header("Origin", ORIGIN).header(AuthCookies.CSRF_HEADER, CSRF)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"countryOrRegion":" China ","city":"Shanghai","dietaryRestrictions":[" Vegan "],"version":0}
						"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.countryOrRegion").value("China"))
				.andExpect(jsonPath("$.version").value(1));
	}

	@Test
	void unknownTravelFieldReturnsValidationFailedWithoutCallingService() throws Exception {
		mockMvc.perform(put("/api/v1/accounts/me/travel-context").principal(principal()).with(request -> {
				request.setCookies(new Cookie(AuthCookies.CSRF, CSRF)); return request; })
				.header("Origin", ORIGIN).header(AuthCookies.CSRF_HEADER, CSRF)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"dietaryRestrictions\":[],\"version\":0,\"accountId\":\"forged\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		verifyNoInteractions(preferences);
	}

	@Test
	void deleteUsesRequiredQueryVersionAndRejectsJsonBody() throws Exception {
		mockMvc.perform(delete("/api/v1/accounts/me/travel-context?version=0").principal(principal()).with(request -> {
				request.setCookies(new Cookie(AuthCookies.CSRF, CSRF)); return request; })
				.header("Origin", ORIGIN).header(AuthCookies.CSRF_HEADER, CSRF)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void deleteRejectsChunkedJsonBodyBeforeCallingService() throws Exception {
		mockMvc.perform(delete("/api/v1/accounts/me/travel-context?version=0").principal(principal()).with(request -> {
				request.setCookies(new Cookie(AuthCookies.CSRF, CSRF)); return request; })
				.header("Origin", ORIGIN).header(AuthCookies.CSRF_HEADER, CSRF)
				.header("Transfer-Encoding", "chunked")
				.contentType(MediaType.APPLICATION_JSON).content("{\"city\":\"Secret Shanghai\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		verifyNoInteractions(preferences);
	}

	@Test
	void getUnexpectedRuntimeFailureReturnsSafeResponseAndSafeLog(CapturedOutput output) throws Exception {
		when(preferences.getTravelContext(ACCOUNT.accountId())).thenThrow(unexpectedRuntimeFailure());

		mockMvc.perform(get("/api/v1/accounts/me/travel-context").principal(principal()))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.traceId").isNotEmpty());

		assertSafePreferenceFailureLog(output);
	}

	@Test
	void replaceUnexpectedRuntimeFailureReturnsSafeResponseAndSafeLog(CapturedOutput output) throws Exception {
		when(preferences.replaceTravelContext(any(), any(), any(), any(), any(), any(), any(), anyLong()))
				.thenThrow(unexpectedRuntimeFailure());

		mockMvc.perform(put("/api/v1/accounts/me/travel-context").principal(principal()).with(request -> {
				request.setCookies(new Cookie(AuthCookies.CSRF, CSRF)); return request; })
				.header("Origin", ORIGIN).header(AuthCookies.CSRF_HEADER, CSRF)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"city\":\"Secret Shanghai\",\"dietaryRestrictions\":[\"Secret allergy\"],\"version\":0}"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.traceId").isNotEmpty());

		assertSafePreferenceFailureLog(output);
	}

	@Test
	void clearUnexpectedRuntimeFailureReturnsSafeResponseAndSafeLog(CapturedOutput output) throws Exception {
		doThrow(unexpectedRuntimeFailure()).when(preferences).clearTravelContext(ACCOUNT.accountId(), 0);

		mockMvc.perform(delete("/api/v1/accounts/me/travel-context?version=0").principal(principal()).with(request -> {
				request.setCookies(new Cookie(AuthCookies.CSRF, CSRF)); return request; })
				.header("Origin", ORIGIN).header(AuthCookies.CSRF_HEADER, CSRF))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.traceId").isNotEmpty());

		assertSafePreferenceFailureLog(output);
	}

	@Test
	void staleVersionReturnsLatestVersion() throws Exception {
		doThrow(new TravelContextVersionConflict(7)).when(preferences)
				.clearTravelContext(ACCOUNT.accountId(), 3);
		mockMvc.perform(delete("/api/v1/accounts/me/travel-context?version=3").principal(principal()).with(request -> {
				request.setCookies(new Cookie(AuthCookies.CSRF, CSRF)); return request; })
				.header("Origin", ORIGIN).header(AuthCookies.CSRF_HEADER, CSRF))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code")
						.value("TRAVEL_CONTEXT_VERSION_CONFLICT")).andExpect(jsonPath("$.latestVersion").value(7));
	}

	@Test
	void patchLanguageRejectsUnsupportedValueAndMissingCsrf() throws Exception {
		mockMvc.perform(patch("/api/v1/accounts/me").principal(principal())
				.contentType(MediaType.APPLICATION_JSON).content("{\"preferredLanguage\":\"en\"}"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
	}

	@Test
	void passwordRejectionIsUniformAndDoesNotLogOrReturnPassword(CapturedOutput output) throws Exception {
		when(authentication.changePassword(ACCOUNT.accountId(), "old-access", "CurrentSecret1",
				"NewSecret2", "NewSecret2")).thenThrow(AuthFailure.of(AuthFailureReason.PASSWORD_CHANGE_REJECTED));
		mockMvc.perform(post("/api/v1/accounts/me:change-password").principal(principal()).with(request -> {
				request.setCookies(new Cookie(AuthCookies.CSRF, CSRF), new Cookie(AuthCookies.ACCESS, "old-access"));
				return request;
			}).header("Origin", ORIGIN).header(AuthCookies.CSRF_HEADER, CSRF)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"CurrentSecret1\",\"newPassword\":\"NewSecret2\",\"confirmNewPassword\":\"NewSecret2\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REJECTED"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("CurrentSecret1"))))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("NewSecret2"))));
		assertThat(output.getAll()).doesNotContain("CurrentSecret1", "NewSecret2", "old-access");
	}

	@Test
	void passwordUnexpectedFailureUsesTheSameStableInternalErrorCode(CapturedOutput output) throws Exception {
		when(authentication.changePassword(ACCOUNT.accountId(), "old-access", "CurrentSecret1",
				"NewSecret2", "NewSecret2")).thenThrow(unexpectedRuntimeFailure());

		mockMvc.perform(post("/api/v1/accounts/me:change-password").principal(principal()).with(request -> {
			request.setCookies(new Cookie(AuthCookies.CSRF, CSRF), new Cookie(AuthCookies.ACCESS, "old-access"));
			return request;
		}).header("Origin", ORIGIN).header(AuthCookies.CSRF_HEADER, CSRF)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"CurrentSecret1\",\"newPassword\":\"NewSecret2\",\"confirmNewPassword\":\"NewSecret2\"}"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.traceId").isNotEmpty());

		assertSafePreferenceFailureLog(output);
		assertThat(output.getAll()).doesNotContain("CurrentSecret1", "NewSecret2", "old-access");
	}

	private UsernamePasswordAuthenticationToken principal() {
		return new UsernamePasswordAuthenticationToken(ACCOUNT, null, List.of());
	}

	private Filter chunkedRequestMetadata() {
		return (request, response, chain) -> {
			HttpServletRequest httpRequest = (HttpServletRequest) request;
			if ("chunked".equalsIgnoreCase(httpRequest.getHeader("Transfer-Encoding"))) {
				chain.doFilter(new HttpServletRequestWrapper(httpRequest) {
					@Override
					public int getContentLength() {
						return -1;
					}

					@Override
					public long getContentLengthLong() {
						return -1;
					}
				}, response);
				return;
			}
			chain.doFilter(request, response);
		};
	}

	private IllegalStateException unexpectedRuntimeFailure() {
		return new IllegalStateException(
				"SELECT secret_table password=SecretPassword Cookie=session Token=secret-token");
	}

	private void assertSafePreferenceFailureLog(CapturedOutput output) {
		assertThat(output.getAll())
				.contains("账号偏好系统错误:", "traceId=", "code=INTERNAL_SERVER_ERROR", "subjectKey=")
				.doesNotContain("SecretPassword", "secret-token", "secret_table", "Secret Shanghai",
						"Secret allergy", ACCOUNT.accountId().toString(), "stackTrace=", "exceptionType=",
						"IllegalStateException");
	}
}
