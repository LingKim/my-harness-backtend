package com.heness.project.account.api;

import com.heness.project.account.application.AuthFailure;
import com.heness.project.account.application.TravelContextVersionConflict;
import com.heness.project.account.domain.AccountRuleViolation;
import com.heness.project.account.domain.TravelContextViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {AccountPreferencesController.class, ChangePasswordController.class})
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true")
final class AccountPreferencesExceptionHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(AccountPreferencesExceptionHandler.class);
	private final AccountAuthExceptionHandler authenticationFailures;

	AccountPreferencesExceptionHandler(AccountAuthExceptionHandler authenticationFailures) {
		this.authenticationFailures = authenticationFailures;
	}

	@ExceptionHandler(AuthFailure.class)
	ResponseEntity<ProblemDetail> authentication(AuthFailure failure, HttpServletResponse response,
			HttpServletRequest request) {
		return authenticationFailures.handle(failure, response, request);
	}

	@ExceptionHandler(TravelContextVersionConflict.class)
	ResponseEntity<ProblemDetail> conflict(TravelContextVersionConflict failure, HttpServletRequest request) {
		ProblemDetail problem = problem(HttpStatus.CONFLICT, "travel-context-version-conflict",
				"旅行上下文已更新", "请重新加载最新内容后重试", "TRAVEL_CONTEXT_VERSION_CONFLICT", request);
		problem.setProperty("latestVersion", failure.latestVersion());
		logSafe(problem, request);
		return response(problem);
	}

	@ExceptionHandler({TravelContextViolation.class, AccountRuleViolation.class})
	ResponseEntity<ProblemDetail> validation(RuntimeException failure, HttpServletRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "请求参数校验失败",
				"请检查提交的字段", "VALIDATION_FAILED", request);
		problem.setProperty("fieldErrors", List.of(fieldError(failure.getMessage())));
		logSafe(problem, request);
		return response(problem);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException failure, HttpServletRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "请求参数校验失败",
				"请检查提交的字段", "VALIDATION_FAILED", request);
		problem.setProperty("fieldErrors", List.of(Map.of(
				"field", "request", "code", "Invalid", "message", "请求字段或格式不合法")));
		logSafe(problem, request);
		return response(problem);
	}

	@ExceptionHandler(RuntimeException.class)
	ResponseEntity<ProblemDetail> unexpected(HttpServletRequest request) {
		ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-server-error", "服务器内部错误",
				"服务器暂时无法处理请求", "INTERNAL_SERVER_ERROR", request);
		LOGGER.error("账号偏好系统错误: traceId={}, code={}, subjectKey={}",
				problem.getProperties().get("traceId"), problem.getProperties().get("code"), subjectKey(request));
		return response(problem);
	}

	private Map<String, String> fieldError(String rule) {
		String field = switch (rule) {
			case "COUNTRY_OR_REGION_INVALID" -> "countryOrRegion";
			case "CITY_INVALID" -> "city";
			case "TRIP_DATE_ORDER_INVALID" -> "tripEndDate";
			case "ASSISTANCE_NEEDS_INVALID" -> "assistanceNeeds";
			case "DIETARY_RESTRICTION_INVALID", "DIETARY_RESTRICTIONS_INVALID", "DIETARY_RESTRICTIONS_TOO_MANY" -> "dietaryRestrictions";
			case "PREFERRED_LANGUAGE_INVALID" -> "preferredLanguage";
			case "DELETE_BODY_NOT_ALLOWED" -> "request";
			default -> "request";
		};
		return Map.of("field", field, "code", rule, "message", "字段不合法");
	}

	private ProblemDetail problem(HttpStatus status, String slug, String title, String detail,
			String code, HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("urn:chinamate:problem:" + slug));
		problem.setTitle(title);
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("code", code);
		problem.setProperty("traceId", UUID.randomUUID().toString());
		return problem;
	}

	private void logSafe(ProblemDetail problem, HttpServletRequest request) {
		LOGGER.warn("账号偏好请求失败: method={}, path={}, traceId={}, code={}, subjectKey={}", request.getMethod(),
				request.getRequestURI(), problem.getProperties().get("traceId"), problem.getProperties().get("code"),
				subjectKey(request));
	}

	private String subjectKey(HttpServletRequest request) {
		if (request.getUserPrincipal() instanceof org.springframework.security.core.Authentication authentication
				&& authentication.getPrincipal() instanceof com.heness.project.account.application.AccountView account) {
			try {
				return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
						account.accountId().toString().getBytes(StandardCharsets.UTF_8)));
			} catch (java.security.NoSuchAlgorithmException failure) {
				throw new IllegalStateException("SHA-256 不可用");
			}
		}
		return "anonymous";
	}

	private ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
		return ResponseEntity.status(problem.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
	}
}
