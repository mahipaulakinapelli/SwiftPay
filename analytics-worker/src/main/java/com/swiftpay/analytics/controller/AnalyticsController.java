package com.swiftpay.analytics.controller;

import com.swiftpay.analytics.dto.VolumeResponse;
import com.swiftpay.analytics.repository.AnalyticsTransactionRepository;
import com.swiftpay.analytics.service.AnalyticsService;
import com.swiftpay.analytics.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single read endpoint for the analytics worker — surfaces the aggregate
 * volume across all settled transactions.
 *
 * <p>Returns a single scalar pair {@code (totalTransactions, totalVolume)}.
 * Cross-currency summation is technically lossy; per-currency aggregation
 * is one repository query change away (see {@code currency} index).</p>
 */
@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /** Constructor-based dependency injection. */
    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** {@code GET /analytics/volume} — count and sum across {@code analytics_transactions}. */
    @GetMapping("/volume")
    public ResponseEntity<ApiResponse<VolumeResponse>> volume() {
        AnalyticsTransactionRepository.VolumeSummary summary = analyticsService.getVolume();
        return ResponseEntity.ok(ApiResponse.success(
                new VolumeResponse(summary.totalTransactions(), summary.totalVolume())
        ));
    }
}