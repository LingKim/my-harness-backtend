package com.heness.project.shared.web.error;

public record FieldViolation(String field, String code, String message) {
}
