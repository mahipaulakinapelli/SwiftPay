package com.swiftpay.ledger.controller;

import com.swiftpay.ledger.exception.GlobalExceptionHandler;
import com.swiftpay.ledger.service.FailureInjector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DebugController.class)
@Import(GlobalExceptionHandler.class)
class DebugControllerTest {

    @Autowired MockMvc mvc;
    @MockBean FailureInjector failureInjector;

    @Test
    void armFailure_ok() throws Exception {
        UUID txId = UUID.randomUUID();
        mvc.perform(post("/debug/fail-tx/" + txId + "?attempts=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempts_to_fail").value(3));
        verify(failureInjector).arm(txId, 3);
    }

    @Test
    void armFailure_invalidAttempts_returns400() throws Exception {
        mvc.perform(post("/debug/fail-tx/" + UUID.randomUUID() + "?attempts=0"))
                .andExpect(status().isBadRequest());
    }
}