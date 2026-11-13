package com.aman.payment.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentLedgerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Ledger Microservice API")
                        .description("Hexagonal Architecture double-entry payment ledger engine featuring " +
                                "two-tier idempotency, transactional outbox pattern, deadlock-free row locking, and continuous reconciliation.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Payment Engineering")
                                .email("dev@payment-ledger.internal"))
                        .license(new License().name("Apache 2.0")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth").addList("ApiKeyAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter 'mock-finance-admin-token' for admin or 'mock-user-token' for user access"))
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-KEY")
                                .description("Enter 'finance-admin-key' for admin or 'user-key' for user access")));
    }
}
