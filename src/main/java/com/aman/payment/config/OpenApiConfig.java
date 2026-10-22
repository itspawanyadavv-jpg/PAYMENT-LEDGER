package com.aman.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentLedgerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Ledger Microservice API")
                        .description("High-throughput, strictly consistent double-entry payment ledger engine featuring " +
                                "two-tier idempotency, transactional outbox pattern, deadlock-free row locking, and continuous reconciliation.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Payment Engineering")
                                .email("dev@payment-ledger.internal"))
                        .license(new License().name("Apache 2.0")));
    }
}
