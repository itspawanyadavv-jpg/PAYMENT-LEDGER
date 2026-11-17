# 04. Backend Deep Dive: Java 17 & Spring Boot 3 Implementation

> **Scope:** Forensic, class-by-class, method-by-method deep dive into the Spring Boot backend, explaining internal mechanics, annotations, dynamic proxies, concurrency primitives, and potential interview traps.

---

## 1. Spring Boot 3 Application Lifecycle & IoC Initialization

When `PaymentApplication.java` executes:
```java
@SpringBootApplication
@EnableScheduling
public class PaymentApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
```

### What Happens Internally Under the Hood:
1. **Bootstrap Phase:**
   - `SpringApplication.run()` initializes the `DefaultBootstrapContext`.
   - Determines application type: `WebApplicationType.SERVLET` (because `spring-boot-starter-web` is on the classpath, embedding Tomcat).
   - Loads `ApplicationContextInitializer` and `ApplicationListener` instances from `META-INF/spring.factories` and `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
2. **Environment & Profile Configuration:**
   - Reads `application.yml` and environment variables. Resolves placeholders (`${SPRING_DATASOURCE_URL:...}`).
3. **ApplicationContext Creation:**
   - Instantiates an `AnnotationConfigServletWebServerApplicationContext`.
   - Starts the embedded **Apache Tomcat 10.1** server on port `8081` (defaulting to servlet threads from `HikariPool`).
4. **Bean Definition & Scanning:**
   - `@SpringBootApplication` combines `@Configuration`, `@EnableAutoConfiguration`, and `@ComponentScan`.
   - Scans recursively from package `com.aman.payment`:
     - Discovers `@Component`, `@Service`, `@Repository`, `@RestController`, `@Configuration`.
     - Creates `BeanDefinition` objects in the `DefaultListableBeanFactory`.
5. **Dependency Injection & Proxy Generation:**
   - Resolves dependencies in constructor injection order.
   - For beans annotated with `@Transactional` (e.g., `PaymentApplicationService`), Spring creates a **CGLIB dynamic subclass proxy**.
   - For beans annotated with `@Scheduled` (e.g., `OutboxPublisher`), registers tasks with `ThreadPoolTaskScheduler`.
6. **Flyway Migration Execution:**
   - Before `EntityManagerFactory` initializes, `FlywayAutoConfiguration` detects `V1__init_schema.sql` under `classpath:db/migration`.
   - Opens a raw JDBC connection, creates the `flyway_schema_history` table if missing, and executes unapplied migrations.
7. **JPA & Hibernate Validation:**
   - Hibernate ORM 6.4 initializes the `EntityManagerFactory`.
   - Because `spring.jpa.hibernate.ddl-auto=validate`, Hibernate validates that all entity mappings (`AccountJpaEntity`, `PaymentJpaEntity`, etc.) strictly match the PostgreSQL database tables created by Flyway. If a column or type differs, the startup aborts immediately with `SchemaManagementException`.
8. **Security Filter Chain Assembly:**
   - `SecurityConfig` builds the `SecurityFilterChain` bean, placing `ApiKeyAuthenticationFilter` and `MockJwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`.

---

## 2. In-Depth Class-by-Class Analysis

### A. `PaymentApplicationService.java` (Application Service)

#### 1. Responsibility:
The central orchestrator of financial transfers. Implements `ProcessPaymentUseCase`, `GetPaymentUseCase`, and `RefundPaymentUseCase`. Coordinates two-tier idempotency, deadlock-free row locking, domain execution, and transactional persistence.

#### 2. Key Annotations:
- `@Service`: Marks the class as a Spring-managed singleton service bean in the IoC container.
- `@Slf4j`: Generates a private static final SLF4J `Logger log`.
- `@Transactional`: In `executeInTransaction`, instructs Spring's `TransactionInterceptor` to open a database transaction, commit on normal exit, and roll back on `RuntimeException` or `Error`.
- `@Lazy PaymentApplicationService self`: **Crucial FAANG Talking Point.**

#### 3. Deep Dive: The `@Lazy self` Injection Pattern
```java
public PaymentApplicationService(
        PaymentRepositoryPort paymentRepository,
        AccountRepositoryPort accountRepository,
        LedgerEntryRepositoryPort ledgerEntryRepository,
        PaymentEventPublisherPort paymentEventPublisher,
        IdempotencyPort idempotencyPort,
        ObjectMapper objectMapper,
        @Lazy PaymentApplicationService self) {
    ...
    this.self = self;
}
```
- **Why this was chosen:** Spring `@Transactional` is implemented via **AOP proxies**. When a public method (`processPayment`) calls another method (`executeInTransaction`) within the same class using `this.executeInTransaction()`, the call bypasses the CGLIB proxy. Therefore, **no transaction would be started!**
- By injecting `@Lazy PaymentApplicationService self`, the call `self.executeInTransaction()` routes through the Spring CGLIB proxy, properly starting the physical PostgreSQL transaction.
- `@Lazy` is required to prevent a circular dependency during constructor injection.

#### 4. The Deadlock Avoidance Algorithm:
```java
// Deadlock avoidance: lock accounts in a fixed UUID order
UUID firstId = command.sourceAccountId().compareTo(command.destinationAccountId()) <= 0
        ? command.sourceAccountId() : command.destinationAccountId();
UUID secondId = firstId.equals(command.sourceAccountId())
        ? command.destinationAccountId() : command.sourceAccountId();

Account firstAccount = accountRepository.findByIdForUpdate(firstId)
        .orElseThrow(() -> new NoSuchElementException("Account not found: " + firstId));
Account secondAccount = accountRepository.findByIdForUpdate(secondId)
        .orElseThrow(() -> new NoSuchElementException("Account not found: " + secondId));
```
- **The Problem:** In a concurrent system, if Alice transfers to Bob while Bob simultaneously transfers to Alice:
  - Thread 1 locks Alice, attempts to lock Bob.
  - Thread 2 locks Bob, attempts to lock Alice.
  - **Result:** Classic circular wait deadlock (Postgres error `40P01: deadlock detected`).
- **The Solution:** By comparing UUIDs (`compareTo <= 0`), both Thread 1 and Thread 2 are forced to lock the lower UUID first and the higher UUID second. Because resource acquisition order is globally identical, circular wait is mathematically impossible.

#### 5. Retry with Exponential Backoff:
```java
private Payment processWithRetry(ProcessPaymentCommand command, String payload) {
    int attempt = 0;
    while (true) {
        try {
            return self.executeInTransaction(command, payload);
        } catch (OptimisticLockingFailureException | CannotAcquireLockException e) {
            attempt++;
            if (attempt >= MAX_RETRIES) throw e;
            log.warn("Retrying payment {} after lock conflict (attempt {}/{})", command.idempotencyKey(), attempt, MAX_RETRIES);
            backoff(attempt);
        }
    }
}
```
- Retries transient lock timeouts (`CannotAcquireLockException`) with backoff (`attempt * 50ms`), dramatically improving system resiliency under heavy burst traffic.

---

### B. `DoubleEntryLedger.java` (Domain Service)

#### 1. Responsibility:
Pure domain invariant calculator. Enforces double-entry bookkeeping rules without any Spring or database ties.

#### 2. Key Method: `postTransfer`
```java
public PostingResult postTransfer(UUID paymentId, Account source, Account destination, Money amount) {
    if (source.getId().equals(destination.getId())) {
        throw new IllegalArgumentException("Source and destination accounts must be distinct");
    }
    if (!amount.isPositive()) {
        throw new IllegalArgumentException("Transfer amount must be positive: " + amount);
    }

    source.debit(amount);
    destination.credit(amount);

    LedgerEntry debitEntry = LedgerEntry.createDebit(paymentId, source.getId(), amount, source.getBalance());
    LedgerEntry creditEntry = LedgerEntry.createCredit(paymentId, destination.getId(), amount, destination.getBalance());

    if (!debitEntry.getAmount().equals(creditEntry.getAmount())) {
        throw new LedgerImbalanceException(paymentId, debitEntry.getAmount(), creditEntry.getAmount());
    }

    return new PostingResult(source, destination, debitEntry, creditEntry);
}
```
- **Interview Highlight:** Notice how `LedgerEntry` captures `balanceAfter` (`source.getBalance()`). This snapshot provides an immutable historical balance audit trail, enabling reconstruction of the balance sheet at any point in time.

---

### C. `Money.java` (Domain Value Object)

#### 1. Responsibility:
Encapsulates financial amounts with fixed precision and currency safety.
```java
public final class Money implements Comparable<Money> {
    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final BigDecimal amount;
    private final String currencyCode;
    ...
```
#### 2. Design Decisions:
- **`final class` with `private final` fields:** Guarantees strict immutability. Value objects must never change state after creation.
- **`RoundingMode.HALF_EVEN` (Banker's Rounding):** Rounds towards the nearest even number. Statistically eliminates cumulative rounding bias across millions of transactions compared to `HALF_UP`.
- **Scale of 4 (`NUMERIC(19,4)`):** Supports fractional currency units (e.g. sub-cent transaction fees, interest accruals) without precision loss.
- **Currency Guard:**
  ```java
  private void assertSameCurrency(Money other) {
      if (!this.currencyCode.equalsIgnoreCase(other.currencyCode)) {
          throw new CurrencyMismatchException(this.currencyCode, other.currencyCode);
      }
  }
  ```

---

### D. `IdempotencyKeyService.java` (Infrastructure Service)

#### 1. Responsibility:
Coordinates in-flight and persistent duplicate prevention across Redis and PostgreSQL.

#### 2. Methods & Transactional Propagations:
1. `checkAndLock(String idempotencyKey, String requestPayload)`:
   - Hashes payload using SHA-256.
   - Checks Postgres `idempotency_records`. If `COMPLETED`, verifies payload hash. If mismatch, throws `DuplicatePaymentException` (protects against key reuse with altered amounts).
   - If key not in DB, executes atomic Redis `setIfAbsent(LOCK_PREFIX + key, hash, 30s)`. If lock cannot be acquired, returns `IN_FLIGHT`.
2. `beginProcessing(String idempotencyKey, String requestPayload)`:
   - Annotated with `@Transactional(propagation = Propagation.MANDATORY)`.
   - **Why `MANDATORY`?** Guarantees this method is NEVER called outside an active transaction. If a developer forgets `@Transactional` on the caller, Spring immediately throws `IllegalTransactionStateException`.
3. `completeProcessing(String idempotencyKey, String responseBody)`:
   - `@Transactional(propagation = Propagation.MANDATORY)`. Updates record to `COMPLETED`, serializes the JSON response, and deletes the Redis lock.
4. `failProcessing(String idempotencyKey)`:
   - Annotated with `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
   - **Why `REQUIRES_NEW`?** If the parent payment transaction throws an exception and rolls back, any DB operations in that transaction are discarded. `REQUIRES_NEW` creates a completely separate physical connection/transaction to record the `FAILED` status, ensuring the idempotency audit record is preserved and the Redis lock is released in the `finally` block.

---

### E. `OutboxPublisher.java` (Scheduled Messaging Relay)

#### 1. Responsibility:
Asynchronously relays pending events from the relational outbox table to Apache Kafka.

#### 2. Implementation:
```java
@Scheduled(fixedDelayString = "${payment.outbox.poll-interval-ms:500}")
@Transactional
public void publishPendingEvents() {
    List<OutboxEventJpaEntity> batch = outboxEventRepository.lockNextBatch(batchSize);
    if (batch.isEmpty()) return;

    for (OutboxEventJpaEntity event : batch) {
        try {
            PaymentEvent kafkaEvent = objectMapper.readValue(event.getPayload(), PaymentEvent.class);
            kafkaTemplate.send(topic, event.getAggregateId().toString(), kafkaEvent).get();
            event.markPublished();
        } catch (Exception e) {
            log.error("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
            event.markFailed();
        }
        outboxEventRepository.save(event);
    }
}
```
#### 3. Key Technical Details:
- **Synchronous Send with `.get()`:** `kafkaTemplate.send(...).get()` blocks until the Kafka broker acknowledges the write (`acks=all`). This guarantees the event is physically committed to Kafka's log before the database status is updated to `PUBLISHED`.
- **Partition Key:** `event.getAggregateId().toString()` is passed as the Kafka message key. In Kafka, messages with the same key are guaranteed to route to the **exact same partition**, preserving strict chronological ordering for each payment.

---

### F. `GlobalExceptionHandler.java` (Presentation Controller Advice)

#### 1. Responsibility:
Catches all uncaught domain and system exceptions across all controllers and translates them into uniform JSON error envelopes.
```java
@RestControllerAdvice
public class GlobalExceptionHandler { ... }
```

#### 2. Exception Mapping Table:

| Exception Class | HTTP Status Code | Error Code | Business Reason |
|---|---|---|---|
| `InsufficientBalanceException` | **422 Unprocessable Entity** | `INSUFFICIENT_BALANCE` | Account does not hold enough liquidity for the transfer. |
| `DuplicatePaymentException` | **409 Conflict** | `DUPLICATE_PAYMENT` | Idempotency key reused with mismatched request payload. |
| `PaymentNotFoundException` | **404 Not Found** | `PAYMENT_NOT_FOUND` | Queried payment UUID or key does not exist. |
| `CurrencyMismatchException` | **400 Bad Request** | `CURRENCY_MISMATCH` | Transfer attempted between conflicting currencies (e.g. USD -> EUR). |
| `AccountInactiveException` | **409 Conflict** | `ACCOUNT_INACTIVE` | Account is FROZEN or CLOSED. |
| `LedgerImbalanceException` | **500 Internal Server Error**| `LEDGER_IMBALANCE` | Critical invariant failure: debit amount != credit amount. |
| `OptimisticLockingFailureException`| **409 Conflict** | `CONCURRENT_MODIFICATION`| Concurrent row update conflict. |
| `MethodArgumentNotValidException` | **400 Bad Request** | `VALIDATION_ERROR` | Bean validation failure (missing field, negative amount). |
| `AccessDeniedException` | **403 Forbidden** | `FORBIDDEN` | Caller lacks `ROLE_FINANCE_ADMIN`. |
| `AuthenticationException` | **401 Unauthorized** | `UNAUTHORIZED` | Missing or invalid authentication token/key. |
