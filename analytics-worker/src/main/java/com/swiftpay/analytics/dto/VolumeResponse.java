package com.swiftpay.analytics.dto;

import java.math.BigDecimal;

/**
 * Payload for {@code GET /analytics/volume}.
 *
 * @param totalTransactions count of rows in {@code analytics_transactions}
 * @param totalVolume       sum of {@code amount} across all rows
 *                          (single scalar across currencies; lossy when multi-currency)
 */
public record VolumeResponse(
        long totalTransactions,
        BigDecimal totalVolume
) {}