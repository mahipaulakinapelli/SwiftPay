package com.swiftpay.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding of {@code swiftpay.topics.*} for the ledger.
 *
 * <p>The ledger consumes {@code paymentInitiated} and produces
 * {@code paymentCompleted} / {@code paymentFailed} — see
 * {@code PaymentEventConsumer} and {@code PaymentEventProducer}.</p>
 */
@ConfigurationProperties(prefix = "swiftpay.topics")
public record TopicsProperties(
        String paymentInitiated,
        String paymentCompleted,
        String paymentFailed
) {}