package com.heness.project.shared.web.error;

import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
final class ApiProblemDetailFactory {

	private static final String PROBLEM_TYPE_PREFIX = "urn:chinamate:problem:";
	private static final String TRACE_ID_KEY = "traceId";
	private static final Comparator<FieldViolation> FIELD_ERROR_ORDER = Comparator
			.comparing(FieldViolation::field)
			.thenComparing(FieldViolation::code)
			.thenComparing(FieldViolation::message);

	ProblemDetail create(ApiErrorCode errorCode, String requestPath, List<FieldViolation> fieldErrors) {
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
				errorCode.status(),
				errorCode.detail()
		);
		problemDetail.setType(URI.create(PROBLEM_TYPE_PREFIX + errorCode.typeSlug()));
		problemDetail.setTitle(errorCode.title());
		problemDetail.setInstance(URI.create(requestPath));
		problemDetail.setProperty("code", errorCode.name());
		problemDetail.setProperty(TRACE_ID_KEY, resolveTraceId());

		if (fieldErrors != null) {
			List<FieldViolation> normalizedErrors = fieldErrors.stream()
					.sorted(FIELD_ERROR_ORDER)
					.distinct()
					.toList();
			problemDetail.setProperty("fieldErrors", normalizedErrors);
		}

		return problemDetail;
	}

	private String resolveTraceId() {
		String currentTraceId = MDC.get(TRACE_ID_KEY);
		return StringUtils.hasText(currentTraceId) ? currentTraceId : UUID.randomUUID().toString();
	}
}
