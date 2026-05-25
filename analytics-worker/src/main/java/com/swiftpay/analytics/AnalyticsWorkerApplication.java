package com.swiftpay.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Spring Boot entrypoint for the {@code analytics-worker}.
 *
 * <p>Consumer-driven projection service: subscribes to the
 * {@code payment-completed} Kafka topic, materializes settled-transaction
 * rows into {@code swiftpay_analytics.analytics_transactions}, and exposes a
 * single read endpoint at {@code GET /analytics/volume}. Listens on
 * port {@code 8083} by default. Runs as a Docker container in compose.</p>
 */
@SpringBootApplication
@EnableKafka
@ConfigurationPropertiesScan(basePackages = "com.swiftpay.analytics.config")
public class AnalyticsWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsWorkerApplication.class, args);
    }
}