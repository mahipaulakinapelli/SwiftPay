package com.swiftpay.ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Top-level OpenAPI / Swagger metadata for the ledger.
 *
 * <p>Springdoc auto-discovers the {@code @RestController} classes; this
 * bean only supplies the high-level info shown at the top of Swagger UI.</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SwiftPay — Ledger Service API")
                        .description("Ledger source-of-truth: balances, transactions, transaction history")
                        .version("v1")
                        .contact(new Contact().name("SwiftPay").email("dev@swiftpay.io")));
    }
}