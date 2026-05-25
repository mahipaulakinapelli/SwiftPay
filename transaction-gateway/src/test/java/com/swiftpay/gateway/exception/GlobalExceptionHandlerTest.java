package com.swiftpay.gateway.exception;

import com.swiftpay.gateway.exception.BusinessException;
import com.swiftpay.gateway.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesDuplicateAs409() {
        var ex = new DuplicateTransactionException(UUID.randomUUID());
        ResponseEntity<ApiResponse<Void>> resp = handler.handleDuplicate(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().error().code()).isEqualTo("DUPLICATE_TRANSACTION");
    }

    @Test
    void handlesBusinessAs422() {
        ResponseEntity<ApiResponse<Void>> resp = handler.handleBusiness(
                new BusinessException("BIZ_X", "boom"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().error().code()).isEqualTo("BIZ_X");
        assertThat(resp.getBody().error().message()).isEqualTo("boom");
    }

    @Test
    void handlesMalformedBodyAs400() {
        ResponseEntity<ApiResponse<Void>> resp = handler.handleUnreadable(
                new HttpMessageNotReadableException("bad json"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error().code()).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void handlesGenericExceptionAs500() {
        ResponseEntity<ApiResponse<Void>> resp = handler.handleGeneric(
                new RuntimeException("kaboom"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void handlesValidationAs400WithFieldErrors() throws NoSuchMethodException {
        // Build a real MethodArgumentNotValidException carrying a binding result
        Method m = getClass().getDeclaredMethod("handlesValidationAs400WithFieldErrors");
        MethodParameter mp = new MethodParameter(m, -1);
        BindingResult br = new BeanPropertyBindingResult(new Object(), "obj");
        br.rejectValue(null, "fieldA", "is required");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(mp, br);

        ResponseEntity<ApiResponse<Void>> resp = handler.handleValidation(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error().code()).isEqualTo("VALIDATION_ERROR");
    }
}