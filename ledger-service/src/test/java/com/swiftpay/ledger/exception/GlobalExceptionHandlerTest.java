package com.swiftpay.ledger.exception;

import com.swiftpay.ledger.exception.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesGenericAs500() {
        var resp = handler.handleGeneric(new RuntimeException("kaboom"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void handlesBusinessAs422() {
        var resp = handler.handleBusiness(new BusinessException("CODE", "msg"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().error().code()).isEqualTo("CODE");
    }

    @Test
    void handlesUnreadableAs400() {
        var resp = handler.handleUnreadable(new HttpMessageNotReadableException("bad"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handlesTypeMismatchAs400() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("userId");
        when(ex.getValue()).thenReturn("abc");

        var resp = handler.handleTypeMismatch(ex);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error().message()).contains("userId").contains("abc");
    }

    @Test
    void handlesConstraintViolationAs400() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation.getMessage()).thenReturn("must be positive");

        var resp = handler.handleParamValidation(new ConstraintViolationException(Set.of(violation)));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error().code()).isEqualTo("VALIDATION_ERROR");
    }
}