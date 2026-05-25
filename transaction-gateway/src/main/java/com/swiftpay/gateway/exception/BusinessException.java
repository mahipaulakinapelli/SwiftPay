package com.swiftpay.gateway.exception;

/**
 * Base type for domain-level failures that should be reported to the caller
 * with a stable error code and a 4xx status.
 *
 * <p>Subclass for specific failure modes (e.g. {@link DuplicateTransactionException}).
 * The {@code @RestControllerAdvice} maps raw {@code BusinessException}s to
 * {@code 422 Unprocessable Entity}.</p>
 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}