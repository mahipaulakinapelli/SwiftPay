package com.swiftpay.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Switches on {@code @Scheduled} support for the gateway.
 *
 * <p>Currently only {@code OutboxPublisher} needs it, but isolating the
 * activation in its own config makes it easy to suppress under
 * {@code @SpringBootTest} via {@code @MockBean} / profile.</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}