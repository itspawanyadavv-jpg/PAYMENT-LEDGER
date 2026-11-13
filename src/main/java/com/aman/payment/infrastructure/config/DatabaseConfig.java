package com.aman.payment.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaRepositories(basePackages = "com.aman.payment.infrastructure.persistence.repository")
@EnableJpaAuditing
@EnableTransactionManagement
public class DatabaseConfig {
}
