package com.swiftpay.gateway.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDetail(
        String code,
        String message,
        List<FieldError> fieldErrors
) {

    public static ErrorDetail of(String code, String message) {
        return new ErrorDetail(code, message, null);
    }

    public static ErrorDetail of(String code, String message, List<FieldError> fieldErrors) {
        return new ErrorDetail(code, message, fieldErrors);
    }

    public record FieldError(String field, String message) {}
}