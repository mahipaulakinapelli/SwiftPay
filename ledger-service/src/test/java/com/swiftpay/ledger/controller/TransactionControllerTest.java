package com.swiftpay.ledger.controller;

import com.swiftpay.ledger.enums.TransactionStatus;
import com.swiftpay.ledger.response.PagedResponse;
import com.swiftpay.ledger.dto.TransactionHistoryItem;
import com.swiftpay.ledger.exception.GlobalExceptionHandler;
import com.swiftpay.ledger.service.TransactionHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TransactionController.class)
@Import(GlobalExceptionHandler.class)
class TransactionControllerTest {

    @Autowired MockMvc mvc;
    @MockBean TransactionHistoryService transactionHistoryService;

    @Test
    void getHistory_ok() throws Exception {
        var item = new TransactionHistoryItem(
                UUID.randomUUID(), 1L, 2L, new BigDecimal("10"), "USD",
                TransactionStatus.COMPLETED, OffsetDateTime.now(), OffsetDateTime.now());
        when(transactionHistoryService.getHistory(eq(1L), any()))
                .thenReturn(PagedResponse.of(List.of(item), 0, 20, 1));

        mvc.perform(get("/v1/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total_elements").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"));
    }

    @Test
    void getHistory_invalidUserId_returns400() throws Exception {
        mvc.perform(get("/v1/transactions/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getHistory_nonNumericUserId_returns400() throws Exception {
        mvc.perform(get("/v1/transactions/abc"))
                .andExpect(status().isBadRequest());
    }
}