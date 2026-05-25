package com.swiftpay.analytics.controller;

import com.swiftpay.analytics.repository.AnalyticsTransactionRepository;
import com.swiftpay.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired MockMvc mvc;
    @MockBean AnalyticsService analyticsService;

    @Test
    void getVolume_returnsAggregate() throws Exception {
        when(analyticsService.getVolume()).thenReturn(
                new AnalyticsTransactionRepository.VolumeSummary(7L, new BigDecimal("162.5000")));

        mvc.perform(get("/analytics/volume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_transactions").value(7))
                .andExpect(jsonPath("$.data.total_volume").value(162.5));
    }
}