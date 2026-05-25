package com.swiftpay.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Top-level OpenAPI / Swagger metadata for the gateway.
 *
 * <p>Springdoc auto-scans {@code @RestController} annotations to build the
 * paths; this bean only contributes the high-level service info (title,
 * description, version, contact) shown at the top of the Swagger UI.</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI swiftPayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SwiftPay — Transaction Gateway API")
                        .description("Public payment-initiation API")
                        .version("v1")
                        .contact(new Contact().name("SwiftPay").email("dev@swiftpay.io")));
    }
}