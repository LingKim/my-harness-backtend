package com.heness.project.shared.web.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice(basePackages = "com.heness.project")
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	private static final String DEFAULT_VALIDATION_MESSAGE = "参数不合法";

	private final ApiProblemDetailFactory problemDetailFactory;

	public GlobalExceptionHandler(ApiProblemDetailFactory problemDetailFactory) {
		this.problemDetailFactory = problemDetailFactory;
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException exception,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		List<FieldViolation> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
				.map(this::toFieldViolation)
				.toList();
		return validationResponse(request, fieldErrors);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException exception,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		List<FieldViolation> fieldErrors = exception.getParameterValidationResults().stream()
				.flatMap(result -> toFieldViolations(result).stream())
				.toList();
		return validationResponse(request, fieldErrors);
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(
			MissingServletRequestParameterException exception,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		FieldViolation fieldError = new FieldViolation(
				exception.getParameterName(),
				"NotNull",
				"缺少必填参数"
		);
		return validationResponse(request, List.of(fieldError));
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(
			TypeMismatchException exception,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		String field = exception instanceof MethodArgumentTypeMismatchException mismatchException
				? mismatchException.getName()
				: "request";
		FieldViolation fieldError = new FieldViolation(field, "typeMismatch", "参数类型不正确");
		return validationResponse(request, List.of(fieldError));
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(
			HttpMessageNotReadableException exception,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {
		return problemResponse(ApiErrorCode.MALFORMED_REQUEST, request, null);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpectedException(Exception exception, HttpServletRequest request) {
		ProblemDetail problemDetail = problemDetailFactory.create(
				ApiErrorCode.INTERNAL_SERVER_ERROR,
				request.getRequestURI(),
				null
		);
		String traceId = String.valueOf(problemDetail.getProperties().get("traceId"));
		List<StackTraceElement> safeStackTrace = List.of(exception.getStackTrace());
		LOGGER.error(
				"未处理的 API 异常: method={}, path={}, traceId={}, exceptionType={}, stackTrace={}",
				request.getMethod(),
				request.getRequestURI(),
				traceId,
				exception.getClass().getName(),
				safeStackTrace
		);
		return response(problemDetail, ApiErrorCode.INTERNAL_SERVER_ERROR.status());
	}

	private ResponseEntity<Object> validationResponse(WebRequest request, List<FieldViolation> fieldErrors) {
		return problemResponse(ApiErrorCode.VALIDATION_FAILED, request, fieldErrors);
	}

	private ResponseEntity<Object> problemResponse(
			ApiErrorCode errorCode,
			WebRequest request,
			List<FieldViolation> fieldErrors) {
		ProblemDetail problemDetail = problemDetailFactory.create(
				errorCode,
				requestPath(request),
				fieldErrors
		);
		return response(problemDetail, errorCode.status());
	}

	private ResponseEntity<Object> response(ProblemDetail problemDetail, HttpStatusCode status) {
		return ResponseEntity.status(status)
				.contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
				.body(problemDetail);
	}

	private FieldViolation toFieldViolation(FieldError fieldError) {
		return new FieldViolation(
				fieldError.getField(),
				constraintCode(fieldError),
				validationMessage(fieldError)
		);
	}

	private List<FieldViolation> toFieldViolations(ParameterValidationResult result) {
		String field = requestParameterName(result.getMethodParameter());
		List<FieldViolation> fieldErrors = new ArrayList<>();
		for (MessageSourceResolvable error : result.getResolvableErrors()) {
			fieldErrors.add(new FieldViolation(
					field,
					constraintCode(error),
					validationMessage(error)
			));
		}
		return fieldErrors;
	}

	private String requestParameterName(MethodParameter methodParameter) {
		RequestParam requestParam = methodParameter.getParameterAnnotation(RequestParam.class);
		if (requestParam != null) {
			String requestParamName = firstNonBlank(requestParam.name(), requestParam.value());
			if (requestParamName != null) {
				return requestParamName;
			}
		}

		PathVariable pathVariable = methodParameter.getParameterAnnotation(PathVariable.class);
		if (pathVariable != null) {
			String pathVariableName = firstNonBlank(pathVariable.name(), pathVariable.value());
			if (pathVariableName != null) {
				return pathVariableName;
			}
		}
		String parameterName = methodParameter.getParameterName();
		return StringUtils.hasText(parameterName) ? parameterName : "request";
	}

	private String firstNonBlank(String first, String second) {
		if (StringUtils.hasText(first)) {
			return first;
		}
		return StringUtils.hasText(second) ? second : null;
	}

	private String constraintCode(MessageSourceResolvable error) {
		String[] codes = error.getCodes();
		if (codes == null || codes.length == 0 || !StringUtils.hasText(codes[0])) {
			return "Invalid";
		}
		String mostSpecificCode = codes[0];
		int separator = mostSpecificCode.indexOf('.');
		return separator >= 0 ? mostSpecificCode.substring(0, separator) : mostSpecificCode;
	}

	private String validationMessage(MessageSourceResolvable error) {
		return StringUtils.hasText(error.getDefaultMessage())
				? error.getDefaultMessage()
				: DEFAULT_VALIDATION_MESSAGE;
	}

	private String requestPath(WebRequest request) {
		if (request instanceof ServletWebRequest servletWebRequest) {
			return servletWebRequest.getRequest().getRequestURI();
		}
		return "/";
	}
}
