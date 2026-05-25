package com.swiftpay.ledger.controller;

import com.swiftpay.ledger.enums.AccountStatus;
import com.swiftpay.ledger.cache.BalanceCacheService;
import com.swiftpay.ledger.dto.BalanceDto;
import com.swiftpay.ledger.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountController.class)
@Import(GlobalExceptionHandler.class)
class AccountControllerTest {

    @Autowired MockMvc mvc;
    @MockBean BalanceCacheService balanceCacheService;

    @Test
    void getBalances_ok() throws Exception {
        when(balanceCacheService.getBalances(1L)).thenReturn(List.of(
                new BalanceDto(11L, "USD", new BigDecimal("100.00"), AccountStatus.ACTIVE)));

        mvc.perform(get("/v1/accounts/1/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].currency").value("USD"))
                .andExpect(jsonPath("$.data[0].account_id").value(11));
    }

    @Test
    void getBalances_invalidUserId_returns400() throws Exception {
        mvc.perform(get("/v1/accounts/-1/balances"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}