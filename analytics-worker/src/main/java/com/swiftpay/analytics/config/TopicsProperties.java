package com.swiftpay.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding of {@code swiftpay.topics.*} from application.yml.
 *
 * <p>The analytics-worker currently only consumes {@code payment-completed},
 * but the full triplet is bound so future consumers (e.g. fraud notifications
 * on {@code payment-failed}) can reuse the same property keys.</p>
 */
@ConfigurationProperties(prefix = "swiftpay.topics")
public record TopicsProperties(
        String paymentInitiated,
        String paymentCompleted,
        String paymentFailed
) {}