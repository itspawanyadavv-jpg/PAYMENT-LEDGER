package com.aman.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Enables JPA repositories, auditing (created_at/updated_at) and explicit
 * transaction management. Kept separate from application.yml so
 * infra-level wiring is discoverable in one place.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.aman.payment.repository")
@EnableJpaAuditing
@EnableTransactionManagement
public class DatabaseConfig {
}
