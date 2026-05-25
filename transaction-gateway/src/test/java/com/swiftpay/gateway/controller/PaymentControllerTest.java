package com.swiftpay.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftpay.gateway.enums.TransactionStatus;
import com.swiftpay.gateway.config.OpenApiConfig;
import com.swiftpay.gateway.dto.PaymentResponse;
import com.swiftpay.gateway.exception.DuplicateTransactionException;
import com.swiftpay.gateway.exception.GlobalExceptionHandler;
import com.swiftpay.gateway.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@Import({ GlobalExceptionHandler.class, OpenApiConfig.class })
class PaymentControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private PaymentService paymentService;

    @Test
    void post_validPayment_returns202() throws Exception {
        UUID txId = UUID.randomUUID();
        when(paymentService.initiatePayment(any())).thenReturn(new PaymentResponse(
                txId, 1L, 2L, new BigDecimal("10.00"), "USD",
                TransactionStatus.PENDING, OffsetDateTime.now()));

        String body = """
                {
                  "sender_id": 1,
                  "receiver_id": 2,
                  "amount": 10.00,
                  "currency": "USD",
                  "transaction_id": "%s"
                }
                """.formatted(txId);

        mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.transaction_id").value(txId.toString()));
    }

    @Test
    void post_invalidBody_returns400ValidationError() throws Exception {
        String body = """
                {
                  "sender_id": -1,
                  "receiver_id": 0,
                  "amount": -50,
                  "currency": "us"
                }
                """;

        mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void post_duplicate_returns409() throws Exception {
        UUID txId = UUID.randomUUID();
        when(paymentService.initiatePayment(any())).thenThrow(new DuplicateTransactionException(txId));

        String body = """
                {
                  "sender_id": 1, "receiver_id": 2,
                  "amount": 10.00, "currency": "USD",
                  "transaction_id": "%s"
                }
                """.formatted(txId);

        mvc.perform(post("/v1/payments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_TRANSACTION"));
    }
}