package com.heness.project.account.api;

import com.heness.project.account.application.AuthFailure;
import com.heness.project.account.application.AuthFailureReason;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

@RestControllerAdvice(basePackages = "com.heness.project.account.api")
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
final class AccountAuthExceptionHandler {

	private final AuthCookieWriter cookies;

	AccountAuthExceptionHandler(AuthCookieWriter cookies) {
		this.cookies = cookies;
	}

	@ExceptionHandler(AuthFailure.class)
	ResponseEntity<ProblemDetail> handle(AuthFailure failure, HttpServletResponse response) {
		ErrorContract contract = contract(failure.reason());
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(contract.status(), contract.detail());
		problem.setType(URI.create("urn:chinamate:problem:" + contract.typeSlug()));
		problem.setTitle(contract.title());
		problem.setProperty("code", failure.reason().name());
		problem.setProperty("traceId", UUID.randomUUID().toString());

		ResponseEntity.BodyBuilder builder = ResponseEntity.status(contract.status())
				.contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON);
		if (failure.reason() == AuthFailureReason.RATE_LIMITED) {
			long seconds = roundedSeconds(failure.retryAfter());
			problem.setProperty("retryAfterSeconds", seconds);
			builder.header(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
		}
		if (failure.reason() == AuthFailureReason.REFRESH_TOKEN_INVALID) {
			cookies.clear(response);
		}
		return builder.body(problem);
	}

	private long roundedSeconds(Duration duration) {
		long seconds = duration.toSeconds();
		return duration.minusSeconds(seconds).isZero() ? seconds : seconds + 1;
	}

	private ErrorContract contract(AuthFailureReason reason) {
		return switch (reason) {
			case REGISTRATION_REJECTED -> new ErrorContract(
					HttpStatus.CONFLICT, "account-registration-rejected", "无法创建账号", "请更换账号或稍后重试");
			case INVALID_CREDENTIALS -> new ErrorContract(
					HttpStatus.UNAUTHORIZED, "invalid-credentials", "登录失败", "账号或密码错误，请检查后重试");
			case RATE_LIMITED -> new ErrorContract(
					HttpStatus.TOO_MANY_REQUESTS, "auth-rate-limited", "登录尝试暂时受限", "请在等待时间结束后重试");
			case ACCESS_TOKEN_INVALID -> new ErrorContract(
					HttpStatus.UNAUTHORIZED, "access-token-invalid", "需要登录", "当前登录状态无效或已过期");
			case REFRESH_TOKEN_INVALID -> new ErrorContract(
					HttpStatus.UNAUTHORIZED, "refresh-token-invalid", "需要重新登录", "当前会话已失效");
			case REFRESH_CONFLICT -> new ErrorContract(
					HttpStatus.CONFLICT, "auth-refresh-conflict", "会话正在刷新", "请使用最新会话重试");
		};
	}

	private record ErrorContract(HttpStatus status, String typeSlug, String title, String detail) {
	}
}
