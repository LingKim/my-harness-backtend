package com.heness.project.shared.web.error;

import org.springframework.http.HttpStatus;

enum ApiErrorCode {

	VALIDATION_FAILED(
			HttpStatus.BAD_REQUEST,
			"validation-failed",
			"请求参数校验失败",
			"请检查提交的字段"
	),
	MALFORMED_REQUEST(
			HttpStatus.BAD_REQUEST,
			"malformed-request",
			"请求格式错误",
			"请求内容无法解析"
	),
	INTERNAL_SERVER_ERROR(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"internal-server-error",
			"服务器内部错误",
			"服务器暂时无法处理请求"
	);

	private final HttpStatus status;
	private final String typeSlug;
	private final String title;
	private final String detail;

	ApiErrorCode(HttpStatus status, String typeSlug, String title, String detail) {
		this.status = status;
		this.typeSlug = typeSlug;
		this.title = title;
		this.detail = detail;
	}

	HttpStatus status() {
		return status;
	}

	String typeSlug() {
		return typeSlug;
	}

	String title() {
		return title;
	}

	String detail() {
		return detail;
	}
}
