package com.swiftpay.gateway.exception;

import com.swiftpay.gateway.exception.BusinessException;
import com.swiftpay.gateway.response.ApiResponse;
import com.swiftpay.gateway.response.ErrorDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Centralised mapping from exception type → HTTP status + {@link ApiResponse} envelope.
 *
 * <p>Handler order matters: the more specific {@link DuplicateTransactionException}
 * handler must precede the generic {@link BusinessException} handler so the 409
 * mapping isn't shadowed.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * {@code @Valid} failed on the request body — collect every field error and
     * return them as {@code field_errors} in the envelope.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorDetail.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorDetail.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ErrorDetail error = ErrorDetail.of("VALIDATION_ERROR", "Request validation failed", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    /** Body couldn't be parsed at all — malformed JSON, missing content, etc. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        ErrorDetail error = ErrorDetail.of("MALFORMED_REQUEST", "Request body is malformed or missing");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    /**
     * Same {@code transaction_id} POSTed twice within the idempotency TTL.
     * Maps to {@code 409 Conflict}.
     */
    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateTransactionException ex) {
        ErrorDetail error = ErrorDetail.of(ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(error));
    }

    /** Fallback for any other {@link BusinessException} — maps to {@code 422 Unprocessable Entity}. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorDetail error = ErrorDetail.of(ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(error));
    }

    /**
     * Last-resort handler. Logs the stack trace server-side and returns a generic
     * {@code 500 INTERNAL_ERROR} — the response's {@code request_id} can be
     * matched against the log line for diagnosis.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorDetail error = ErrorDetail.of("INTERNAL_ERROR", "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(error));
    }
}
