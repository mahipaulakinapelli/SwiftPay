package com.swiftpay.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Spring Boot entrypoint for the {@code ledger-service}.
 *
 * <p>The financial source of truth: owns {@code swiftpay_ledger}'s {@code users},
 * {@code accounts}, {@code transactions}, and {@code transaction_audit} tables.
 * Consumes {@code payment-initiated} from Kafka to debit / credit, then
 * publishes either {@code payment-completed} or {@code payment-failed}. Also
 * exposes balance and history HTTP APIs on port {@code 8082}.</p>
 *
 * <p>{@link EnableKafka} switches on {@code @KafkaListener} detection; the
 * {@code @RetryableTopic}-decorated consumer in {@code kafka/} needs this.</p>
 */
@SpringBootApplication
@EnableKafka
@ConfigurationPropertiesScan(basePackages = "com.swiftpay.ledger.config")
public class LedgerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerServiceApplication.class, args);
    }
}