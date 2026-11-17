# 11. Testing & Debugging Masterclass: Testcontainers & Concurrency

> **Scope:** Full analysis of the 25-test suite, unit vs. integration testing paradigms, Testcontainers singleton lifecycle mechanics, Mockito techniques, and 7 hands-on production debugging scenarios.

---

## 1. Test Suite Architecture & Coverage Matrix

The repository features 7 dedicated test classes comprising **25 comprehensive tests** running against both pure JVM domain mocks and real containerized infrastructure:

| Test Class | Category | Infrastructure Used | Key Guarantee Verified |
|---|---|---|---|
| `MoneyTest.java` | **Pure Unit Test** | In-Memory JVM | Precision arithmetic, Banker's rounding (`HALF_EVEN`), currency mismatch guards. |
| `DoubleEntryLedgerTest.java` | **Pure Domain Test** | In-Memory JVM | Invariant check: `debit.amount == credit.amount`, balance deductions, distinct account validation. |
| `ConcurrentPaymentTest.java` | **Concurrency Integration**| Testcontainers (Postgres, Redis) + Embedded Kafka | 20 parallel threads executing debits; zero lost updates; zero deadlocks on bidirectional payments. |
| `IdempotencyTest.java` | **Resilience Integration** | Testcontainers (Postgres, Redis) | Replay returns identical response body with zero money moved; mismatched payload throws 409 Conflict. |
| `LedgerInvariantTest.java` | **Audit Integration** | Testcontainers (Postgres) | Every payment creates exactly 2 legs; account balance strictly equals `SUM(credits) - SUM(debits)`. |
| `FailureRecoveryTest.java` | **Fault-Tolerance Integration**| Testcontainers (Postgres, Redis) + Embedded Kafka | Outbox events reach `PUBLISHED` status via Awaitility; failed payments leave zero orphaned records. |
| `SecurityTest.java` | **Security Integration** | Spring MockMvc + Testcontainers | Unauthenticated 401; public Swagger access; standard user 403 on `/reconcile`; admin key 200 OK. |

---

## 2. Unit Testing vs. Integration Testing Philosophy

### Pure Domain Unit Tests (`DoubleEntryLedgerTest.java`):
- **Philosophy:** No Spring Context (`@SpringBootTest` is NOT used). Executes in `< 200ms`.
- **Why:** The financial domain logic must be completely isolated from database drivers or web frameworks.
```java
@Test
void shouldPostBalancedEntries() {
    Account source = Account.create("ACC-001", "Alice", "USD", Money.of("500.00", "USD"));
    Account dest = Account.create("ACC-002", "Bob", "USD", Money.of("100.00", "USD"));
    
    PostingResult result = ledger.postTransfer(UUID.randomUUID(), source, dest, Money.of("200.00", "USD"));
    
    assertThat(result.debitEntry().getAmount()).isEqualTo(result.creditEntry().getAmount());
    assertThat(source.getBalance()).isEqualTo(Money.of("300.00", "USD"));
    assertThat(dest.getBalance()).isEqualTo(Money.of("300.00", "USD"));
}
```

### True Integration Tests (`ConcurrentPaymentTest.java`):
- **Philosophy:** Mocks lie about concurrency and locking! You cannot test `SELECT ... FOR UPDATE` or Redis `SETNX` using Mockito or H2.
- **Why:** True pessimistic row locks and race conditions can only be verified against real PostgreSQL and Redis engines.

---

## 3. The Testcontainers Singleton Pattern (The Hard-Won Fix)

In our initial forensic audit, the test suite failed with `Connection refused: getsockopt` because JUnit Jupiter shut down the Postgres container between test classes while Spring Boot kept the cached ApplicationContext pointing to the closed port.

### The Problematic Anti-Pattern:
```java
// ANTI-PATTERN: Shuts down container after each test class!
@Testcontainers
public abstract class AbstractIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = ...;
}
```

### The Production Singleton Container Solution (`AbstractIntegrationTest.java`):
```java
public abstract class AbstractIntegrationTest {
    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;

    static {
        // Starts ONCE when class is loaded; stays alive across ALL test classes
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
        registry.add("spring.kafka.bootstrap-servers", 
            () -> System.getProperty("spring.embedded.kafka.brokers", "localhost:9092"));
    }
}
```
- **Interview Takeaway:** Explaining how you diagnosed and resolved the Testcontainers JVM lifecycle conflict with Spring's `ApplicationContext` cache immediately proves deep hands-on expertise.

---

## 4. Practical Debugging Scenarios Based on Actual Project Code

### Scenario 1: API Returns 500 `LEDGER_IMBALANCE`
- **Location:** `DoubleEntryLedger.java` line 34.
- **Root Cause:** A developer altered the credit leg to deduct fees without debiting an offsetting account, causing `debit.amount != credit.amount`.
- **How to Debug:**
  1. Inspect the exception payload in `GlobalExceptionHandler.java`:
     ```json
     {
       "error": "LEDGER_IMBALANCE",
       "message": "Debit amount 100.0000 does not match credit amount 95.0000 for payment ..."
     }
     ```
  2. Search `ledger_entries` for that `payment_id`:
     ```sql
     SELECT entry_type, amount FROM ledger_entries WHERE payment_id = '<ID>';
     ```
  3. Ensure fee deductions are credited to a `FEE_REVENUE_ACCOUNT` so that total debits equal total credits.

---

### Scenario 2: Redis Connection Failure during Payment Check
- **Location:** `IdempotencyKeyService.java` line 57.
- **Root Cause:** Redis container crashed or network partitioned.
- **How the Code Reacts:** If Redis is down, `redisTemplate.opsForValue().setIfAbsent()` throws `RedisConnectionException`.
- **Fix:** In `IdempotencyKeyService`, wrap the Redis call in a `try/catch` fallback:
  ```java
  try {
      Boolean acquired = redisTemplate.opsForValue().setIfAbsent(...);
      if (Boolean.FALSE.equals(acquired)) return new LookupOutcome(CheckResult.IN_FLIGHT, Optional.empty());
  } catch (RedisException ex) {
      log.warn("Redis unavailable, falling back to PostgreSQL durable idempotency: {}", ex.getMessage());
  }
  ```

---

### Scenario 3: `DuplicatePaymentException` on Valid User Retry
- **Location:** `IdempotencyKeyService.java` line 46.
- **Root Cause:** Client modified the request body (e.g. changed amount from $50 to $60) while reusing the old `idempotencyKey`.
- **How to Trace:**
  ```sql
  SELECT request_hash, response_body FROM idempotency_records WHERE idempotency_key = '<KEY>';
  ```
  Compute SHA-256 hash of incoming payload and compare. The exception is working as designed to prevent request tampering.

---

### Scenario 4: Concurrent Opposing Payments Cause Lock Timeout
- **Location:** `PaymentApplicationService.java` line 95.
- **Root Cause:** Two transactions attempted to lock the same accounts. If account sorting is accidentally removed, Postgres throws deadlock error `40P01`.
- **How to Debug:** Check PostgreSQL lock wait logs:
  ```sql
  SELECT waiting.locktype, waiting.relation::regclass, waiting.mode, waiting.pid
  FROM pg_locks waiting
  JOIN pg_locks blocking ON waiting.locktype = blocking.locktype
  WHERE NOT waiting.granted;
  ```
  Ensure `firstId.compareTo(secondId) <= 0` ordering is strictly preserved.

---

### Scenario 5: Outbox Events Stuck in `PENDING`
- **Location:** `OutboxPublisher.java`.
- **Root Cause:** `@Scheduled` scheduler thread pool is starved or Kafka broker is unreachable.
- **How to Debug:**
  1. Inspect Kafka consumer group lag:
     ```bash
     docker exec -it payment-ledger-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
       --bootstrap-server localhost:9092 --describe --group payment-ledger-consumer
     ```
  2. Verify Spring's task scheduler has free threads: ensure `spring.task.scheduling.pool.size >= 2`.

---

### Scenario 6: Flyway Migration Checksum Mismatch
- **Root Cause:** A developer manually edited `V1__init_schema.sql` after it had already run on the database.
- **Error:** `FlywayException: Validate failed: Migration checksum mismatch for migration version 1`.
- **Resolution:**
  - Never edit an existing migration file in production! Always create a new file: `V2__add_new_index.sql`.
  - In local development only, clean and re-run:
    ```sql
    DELETE FROM flyway_schema_history WHERE version = '1';
    ```

---

### Scenario 7: Unauthenticated Browser Request to `/api/v1/ledger/reconcile`
- **Location:** `SecurityConfig.java` line 58.
- **Symptom:** Web dashboard displays `Reconciliation failed: 403 Forbidden`.
- **Resolution:**
  Pass the required administrative key in `app.js`:
  ```javascript
  const res = await fetch('/api/v1/ledger/reconcile', {
      method: 'POST',
      headers: { 'X-API-KEY': 'finance-admin-key' }
  });
  ```
