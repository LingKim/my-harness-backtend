package com.heness.project.account.api;

import com.heness.project.account.application.AccountAuthenticationService;
import com.heness.project.account.application.AccountView;
import com.heness.project.account.application.AuthFailure;
import com.heness.project.account.application.AuthFailureReason;
import com.heness.project.account.application.IssuedAuthentication;
import com.heness.project.account.application.SecretToken;
import com.heness.project.account.infrastructure.security.AuthProperties;
import com.heness.project.shared.web.error.ApiProblemDetailFactory;
import com.heness.project.shared.web.error.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountApiTests {

	private static final String ORIGIN = "http://localhost:3000";
	private static final String CSRF = "csrf-value";
	private static final Instant NOW = Instant.parse("2026-07-27T08:00:00Z");

	private AccountAuthenticationService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		service = mock(AccountAuthenticationService.class);
		AuthProperties properties = new AuthProperties(
				true,
				"test-token-pepper-32-characters-minimum",
				"test-failure-pepper-32-characters-minimum",
				Duration.ofMinutes(30),
				Duration.ofDays(7),
				Duration.ofSeconds(5),
				12,
				true,
				List.of()
		);
		AuthCookieWriter cookieWriter = new AuthCookieWriter(properties, Clock.fixed(NOW, ZoneOffset.UTC));
		AccountController accounts = new AccountController(service, cookieWriter);
		AuthSessionController sessions = new AuthSessionController(
				service,
				cookieWriter,
				new ClientIpResolver(properties)
		);
		CsrfTokenController csrf = new CsrfTokenController(new CsrfTokenGenerator(), cookieWriter);
		mockMvc = MockMvcBuilders.standaloneSetup(accounts, sessions, csrf)
				.setControllerAdvice(
						new AccountAuthExceptionHandler(cookieWriter),
						new GlobalExceptionHandler(new ApiProblemDetailFactory()))
				.addFilters(new AuthCsrfFilter(ORIGIN))
				.build();
	}

	@Test
	void 注册返回账号且Token只进入安全Cookie() throws Exception {
		when(service.register(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.eq(true)))
				.thenReturn(authentication());

		mockMvc.perform(post("/api/v1/accounts")
					.header("Origin", ORIGIN)
					.header(AuthCookies.CSRF_HEADER, CSRF)
					.cookie(new Cookie(AuthCookies.CSRF, CSRF))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"accountName":"China_2026","password":"Password123","confirmPassword":"Password123","termsAccepted":true}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accountName").value("China_2026"))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw-access"))))
				.andExpect(cookie().secure(AuthCookies.ACCESS, true))
				.andExpect(cookie().httpOnly(AuthCookies.ACCESS, true))
				.andExpect(cookie().secure(AuthCookies.REFRESH, true))
				.andExpect(cookie().httpOnly(AuthCookies.REFRESH, true));
	}

	@Test
	void 注册校验失败返回字段错误且不调用业务() throws Exception {
		mockMvc.perform(post("/api/v1/accounts")
					.header("Origin", ORIGIN)
					.header(AuthCookies.CSRF_HEADER, CSRF)
					.cookie(new Cookie(AuthCookies.CSRF, CSRF))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"accountName":"x","password":"short","confirmPassword":"different","termsAccepted":false}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void 缺少CSRF证明在业务处理前返回403() throws Exception {
		mockMvc.perform(post("/api/v1/auth-sessions")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"accountName\":\"China_2026\",\"password\":\"Password123\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	void 登录失败不区分账号不存在或密码错误() throws Exception {
		when(service.login(anyString(), anyString(), anyString()))
				.thenThrow(AuthFailure.of(AuthFailureReason.INVALID_CREDENTIALS));

		mockMvc.perform(post("/api/v1/auth-sessions")
					.header("Origin", ORIGIN)
					.header(AuthCookies.CSRF_HEADER, CSRF)
					.cookie(new Cookie(AuthCookies.CSRF, CSRF))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"accountName\":\"China_2026\",\"password\":\"Wrong123\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.detail").value("账号或密码错误，请检查后重试"));
	}

	@Test
	void 限流返回RetryAfter头和秒数() throws Exception {
		when(service.login(anyString(), anyString(), anyString()))
				.thenThrow(AuthFailure.rateLimited(Duration.ofSeconds(61)));

		mockMvc.perform(post("/api/v1/auth-sessions")
					.header("Origin", ORIGIN)
					.header(AuthCookies.CSRF_HEADER, CSRF)
					.cookie(new Cookie(AuthCookies.CSRF, CSRF))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"accountName\":\"China_2026\",\"password\":\"Wrong123\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "61"))
				.andExpect(jsonPath("$.retryAfterSeconds").value(61));
	}

	@Test
	void 刷新成功轮换Cookie且响应体为空() throws Exception {
		when(service.refresh("refresh-cookie")).thenReturn(authentication());

		mockMvc.perform(post("/api/v1/auth-sessions:refresh")
					.header("Origin", ORIGIN)
					.header(AuthCookies.CSRF_HEADER, CSRF)
					.cookie(new Cookie(AuthCookies.CSRF, CSRF), new Cookie(AuthCookies.REFRESH, "refresh-cookie")))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""))
				.andExpect(cookie().value(AuthCookies.ACCESS, "raw-access"));
	}

	@Test
	void 当前账号来自SecurityContext而不是请求体身份() throws Exception {
		AccountView account = authentication().account();

		mockMvc.perform(get("/api/v1/accounts/me")
					.principal(new UsernamePasswordAuthenticationToken(account, null, List.of())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountId").value(account.accountId().toString()));
	}

	@Test
	void 重复退出仍收敛为204并清Cookie() throws Exception {
		mockMvc.perform(delete("/api/v1/auth-sessions/current")
					.header("Origin", ORIGIN)
					.header(AuthCookies.CSRF_HEADER, CSRF)
					.cookie(new Cookie(AuthCookies.CSRF, CSRF)))
				.andExpect(status().isNoContent())
				.andExpect(cookie().maxAge(AuthCookies.ACCESS, 0))
				.andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));
	}

	private IssuedAuthentication authentication() {
		AccountView account = new AccountView(
				UUID.fromString("78cd6aa7-7bc0-4d71-b1ab-e26f4fdf570f"),
				"China_2026",
				NOW.minusSeconds(60)
		);
		return new IssuedAuthentication(
				account,
				new SecretToken("raw-access", "digest-access"),
				new SecretToken("raw-refresh", "digest-refresh"),
				NOW.plus(Duration.ofMinutes(30)),
				NOW.plus(Duration.ofDays(7))
		);
	}
}
