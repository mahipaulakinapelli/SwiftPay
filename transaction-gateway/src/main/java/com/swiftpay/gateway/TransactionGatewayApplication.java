package com.swiftpay.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot entrypoint for the {@code transaction-gateway} service.
 *
 * <p>Bootstraps the HTTP edge that accepts {@code POST /v1/payments},
 * persists a {@code PENDING} record into {@code swiftpay_gateway}, and
 * publishes a {@code PaymentInitiatedEvent} onto Kafka for the
 * {@code ledger-service} to settle. Listens on port {@code 8081} by
 * default.</p>
 *
 * <p>{@link ConfigurationPropertiesScan} picks up
 * {@code TopicsProperties} from the {@code config} package.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.swiftpay.gateway.config")
public class TransactionGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionGatewayApplication.class, args);
    }
}