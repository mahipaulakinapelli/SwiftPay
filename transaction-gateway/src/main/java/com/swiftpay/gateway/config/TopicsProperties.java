package com.swiftpay.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding of {@code swiftpay.topics.*} from application.yml.
 *
 * <p>Keeps topic names out of business code — references go through
 * {@code topicsProperties.paymentInitiated()} rather than literal strings.</p>
 *
 * @param paymentInitiated topic for newly accepted payments (gateway → ledger)
 * @param paymentCompleted topic for settled payments (ledger → analytics + others)
 * @param paymentFailed    topic for business-rule failures (ledger → fraud / notifications)
 */
@ConfigurationProperties(prefix = "swiftpay.topics")
public record TopicsProperties(
        String paymentInitiated,
        String paymentCompleted,
        String paymentFailed
) {}