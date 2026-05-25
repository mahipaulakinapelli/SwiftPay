package com.swiftpay.ledger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Switches on {@code @Scheduled} support for the ledger so
 * {@code OutboxPublisher} runs on its fixed-delay cadence.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}