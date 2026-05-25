package com.swiftpay.gateway.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uniform HTTP response envelope used by {@code transaction-gateway}.
 *
 * <p>Each service owns its own copy of this shape (see Contract Ownership in the
 * root README). The wire shape is intentionally identical across services so
 * clients see one envelope:</p>
 *
 * <pre>{@code { success, data, error, timestamp, request_id }}</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorDetail error,
        OffsetDateTime timestamp,
        String requestId
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, OffsetDateTime.now(), UUID.randomUUID().toString());
    }

    public static <T> ApiResponse<T> error(ErrorDetail error) {
        return new ApiResponse<>(false, null, error, OffsetDateTime.now(), UUID.randomUUID().toString());
    }
}