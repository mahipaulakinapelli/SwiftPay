package com.swiftpay.ledger.exception;

import com.swiftpay.ledger.exception.BusinessException;
import com.swiftpay.ledger.response.ApiResponse;
import com.swiftpay.ledger.response.ErrorDetail;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Centralised mapping of exception types to HTTP responses for the ledger.
 *
 * <p>The ledger exposes path-variable validation ({@code @Min(1)} on
 * {@code userId}) so we need a dedicated handler for
 * {@link ConstraintViolationException} — that fires for path / query
 * validation, whereas {@link MethodArgumentNotValidException} fires for
 * request-body validation. Missing either handler lets a 400 leak as a 500.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Body validation (e.g. {@code @Valid} on {@code @RequestBody}). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<ErrorDetail.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorDetail.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ErrorDetail error = ErrorDetail.of("VALIDATION_ERROR", "Request validation failed", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    /** Path / query parameter validation (e.g. {@code @Min} on a {@code @PathVariable}). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParamValidation(ConstraintViolationException ex) {
        List<ErrorDetail.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new ErrorDetail.FieldError(
                        v.getPropertyPath().toString(),
                        v.getMessage()))
                .toList();
        ErrorDetail error = ErrorDetail.of("VALIDATION_ERROR", "Request validation failed", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    /** Non-numeric path variable where a {@code Long} was expected, etc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = "Parameter '" + ex.getName() + "' has invalid value: " + ex.getValue();
        ErrorDetail error = ErrorDetail.of("VALIDATION_ERROR", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    /** Body couldn't be parsed at all. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        ErrorDetail error = ErrorDetail.of("MALFORMED_REQUEST", "Request body is malformed or missing");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    /** Any other {@link BusinessException} maps to 422. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorDetail error = ErrorDetail.of(ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(error));
    }

    /** Catch-all. Logs the stack and returns a generic 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorDetail error = ErrorDetail.of("INTERNAL_ERROR", "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(error));
    }
}
