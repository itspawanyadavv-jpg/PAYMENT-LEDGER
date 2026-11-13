package com.aman.payment.test;

import com.aman.payment.PaymentApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for integration tests. Spins up real Postgres and Redis
 * containers plus an in-process embedded Kafka broker, so tests exercise
 * actual row locking, actual Redis SETNX semantics, and actual Kafka
 * delivery instead of mocks - the concurrency and durability guarantees
 * this service makes are only meaningful when tested against the real
 * infrastructure they depend on.
 *
 * Uses the Singleton Container pattern (without @Testcontainers/@Container)
 * so that containers are started once and shared across all integration tests
 * without being prematurely shut down between test classes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = PaymentApplication.class)
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"payment.events"})
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                .withDatabaseName("payment_ledger")
                .withUsername("payment_user")
                .withPassword("payment_pass");
        POSTGRES.start();

        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers", "localhost:9092"));
    }
}
