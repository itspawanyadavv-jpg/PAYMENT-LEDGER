# 12. Project Interview Question Bank: 100+ FAANG & FinTech Questions

> **Scope:** 100+ rigorous, project-specific technical interview questions categorized across 13 core domains. Each question includes the interviewer's hidden motive, expected answer, deep technical explanation, follow-ups, and common candidate traps.

---

## Category 1: Project Overview & Core Fundamentals (Q1 – Q8)

### Q1: Can you give a 2-minute elevator pitch of your Payment Ledger project?
- **Interviewer's Intention:** Tests your communication clarity, high-level structural thinking, and ability to summarize complex software without rambling.
- **Expected Answer:**  
  *"The Payment Ledger is an enterprise-grade financial microservice built in Java 17 and Spring Boot 3.2.5 that solves the classic challenges of distributed financial systems: double-spending, race conditions, dual-write failures, and auditability. It implements strict double-entry bookkeeping where every money movement posts balanced, immutable debit and credit entries. To eliminate lost updates and deadlocks under concurrent load, it acquires pessimistic row-level locks on accounts in a deterministic lexicographical UUID order. It features a two-tier idempotency layer combining sub-millisecond Redis distributed locking with durable PostgreSQL payload-hash verification. Finally, it uses the Transactional Outbox Pattern to publish events to Apache Kafka without dual-write inconsistencies, and includes a continuous reconciliation engine to detect any balance drift."*
- **Common Mistake:** Describing it as a simple "banking CRUD app with a database and APIs."

### Q2: What exact problem does double-entry bookkeeping solve compared to a single balance column?
- **Interviewer's Intention:** Tests whether you understand the financial domain rationale or just copied a design pattern.
- **Expected Answer:**  
  In a single-column architecture (`balance = balance - 100`), a database glitch or developer bug can corrupt balances without leaving a mathematical trace. Double-entry bookkeeping guarantees that the source of truth is an immutable, append-only ledger (`ledger_entries`) where every movement creates equal and opposite debit and credit legs. The balance is merely a denormalized cache that can be reconstructed at any moment by summing ledger entries.
- **Follow-up:** *"How do you handle deposits from outside the system without breaking double-entry invariants?"*
- **Answer to Follow-up:** *"We use central system reserve clearing accounts (e.g. `SYSTEM_RESERVE_USD`). A customer deposit is a `DEBIT SYSTEM_RESERVE` and `CREDIT CUSTOMER_ACCOUNT`, keeping total system liquidity invariant."*

### Q3: Why is floating-point arithmetic (`double` / `float`) forbidden in financial applications?
- **Expected Answer:** IEEE 754 floating-point representations use binary fractions (`base-2`), which cannot represent common base-10 decimal fractions like `0.1` or `0.01` precisely. In our codebase, we encapsulate all currency in an immutable `Money` Value Object wrapping `BigDecimal` configured with a scale of 4 and `RoundingMode.HALF_EVEN` (Banker's Rounding).

### Q4: What is Banker's Rounding (`HALF_EVEN`) and why use it over `HALF_UP`?
- **Expected Answer:** `HALF_UP` always rounds `.5` upwards, creating a cumulative positive statistical bias across millions of transactions. `HALF_EVEN` rounds towards the nearest even integer (e.g., `2.5 -> 2`, `3.5 -> 4`), balancing upward and downward roundings to zero net drift.

### Q5: What is the difference between an Entity and a Value Object in Domain-Driven Design?
- **Expected Answer:** An **Entity** (`Account`, `Payment`) has a unique, persistent identity (`UUID id`) that remains the same even if its attributes change. A **Value Object** (`Money`) has no identity and is defined entirely by its attributes; two `Money` instances with `$100.00 USD` are interchangeable and strictly immutable.

### Q6: What happens if an account status is `FROZEN` and a payment request arrives?
- **Expected Answer:** The domain model (`Account.java`) executes `assertActive()`. If status is `FROZEN` or `CLOSED`, it throws `AccountInactiveException`, which `GlobalExceptionHandler` translates to `409 Conflict`.

### Q7: Can a closed account be reopened?
- **Expected Answer:** In our domain model (`Account.java`), `activate()` explicitly checks `if (status == CLOSED) throw new IllegalStateException()`. Once closed, an account is permanently immutable.

### Q8: What condition must be met before an account can be closed?
- **Expected Answer:** The account balance must be exactly zero (`balance.isZero()`). Attempting to close an account holding funds throws an exception to prevent orphaned capital.

---

## Category 2: Hexagonal Architecture & Design Patterns (Q9 – Q16)

### Q9: Explain Hexagonal Architecture (Ports & Adapters) as implemented in this repository.
- **Expected Answer:**  
  The architecture isolates the **Domain Core** (`domain.model`) from external frameworks and technologies.
  - **Inbound (Driving) Ports:** Interfaces defining use cases (`ProcessPaymentUseCase`). Controllers call ports.
  - **Application Services:** Implement the use cases (`PaymentApplicationService`).
  - **Outbound (Driven) Ports:** Interfaces defining data needs (`AccountRepositoryPort`, `IdempotencyPort`).
  - **Driven Adapters:** Infrastructure implementations (`AccountRepositoryAdapter`, `IdempotencyAdapter`) talking to PostgreSQL and Redis.

### Q10: Why did you separate JPA entities (`AccountJpaEntity`) from Domain models (`Account`)?
- **Interviewer's Intention:** Tests understanding of separation of concerns vs. convenience.
- **Expected Answer:**  
  JPA entities require no-arg constructors, mutable setters for Hibernate hydration, and framework annotations (`@Entity`, `@Table`, `@Column`). A pure domain model must be clean, enforce invariants in its constructor, and contain zero framework dependencies. Mappers (`AccountJpaMapper`) translate between the two representations.
- **Common Mistake:** Saying "it's just best practice" without explaining Hibernate's reflection requirements.

### Q11: What is the Anemic Domain Model anti-pattern?
- **Expected Answer:** Placing business logic in service classes while domain classes are mere "data bags" with getters and setters. In our project, `Account.debit()` and `Account.credit()` enforce their own business invariants.

### Q12: Why did you inject `@Lazy PaymentApplicationService self`?
- **Expected Answer:** To overcome Spring's CGLIB proxy self-invocation limitation. When `processPayment` calls `executeInTransaction`, calling `this.executeInTransaction()` bypasses the dynamic proxy, disabling `@Transactional`. Calling `self.executeInTransaction()` routes through the proxy.

### Q13: What is the Factory pattern used in your domain?
- **Expected Answer:** Static factory methods `Account.create(...)` and `Account.createSystemAccount(...)` encapsulate initialization logic and ensure accounts always start with version 0 and valid status.

### Q14: How does your architecture satisfy the Single Responsibility Principle (SRP)?
- **Expected Answer:** Controllers only handle HTTP parsing/validation; Application Services only orchestrate workflow; Domain models only enforce business invariants; Adapters only handle persistence/messaging.

### Q15: How does your architecture satisfy the Dependency Inversion Principle (DIP)?
- **Expected Answer:** High-level application services depend on abstractions (`AccountRepositoryPort`), not on low-level details (`SpringDataAccountRepository`).

### Q16: How would you swap PostgreSQL for MongoDB without changing business logic?
- **Expected Answer:** Write a `MongoAccountRepositoryAdapter` implementing `AccountRepositoryPort`. The domain core and application services require zero line changes.

---

## Category 3: Concurrency, Multithreading & Row Locking (Q17 – Q25)

### Q17: How does your system prevent the "Lost Update" problem?
- **Expected Answer:** When reading accounts for a transfer, we use pessimistic locking: `SELECT ... FOR UPDATE`. This instructs PostgreSQL to lock the row tuples, forcing any concurrent transactions modifying the same accounts to block until the first commits.

### Q18: How do you mathematically prevent deadlocks during concurrent bidirectional transfers?
- **Expected Answer:**  
  A deadlock occurs when Thread 1 holds Account A and waits for B, while Thread 2 holds B and waits for A (circular wait).  
  In `PaymentApplicationService`, we enforce **global lock acquisition ordering by UUID**:
  ```java
  UUID firstId = sourceId.compareTo(destId) <= 0 ? sourceId : destId;
  UUID secondId = firstId.equals(sourceId) ? destId : sourceId;
  ```
  Both threads are forced to lock the smaller UUID first. Since neither thread can hold the higher UUID while waiting for the lower one, circular wait is impossible.

### Q19: What is the difference between Pessimistic Locking and Optimistic Locking?
- **Expected Answer:**  
  - **Pessimistic Locking (`@Lock(PESSIMISTIC_WRITE)`):** Locks the row at the database level on read. Other writers wait. Best for high-contention financial systems to guarantee zero rollbacks.
  - **Optimistic Locking (`@Version`):** Checks version column on update (`UPDATE ... WHERE version = 1`). If another transaction changed the version, throws `OptimisticLockingFailureException`. Best for low-contention read-heavy systems.

### Q20: What happens if an account lock times out?
- **Expected Answer:** Spring throws `CannotAcquireLockException`. Our `processWithRetry` method catches this and executes exponential backoff (`attempt * 50ms`) up to 3 retries.

### Q21: What is a Race Condition in a bank transfer?
- **Expected Answer:** Two transactions read balance `$100` simultaneously. Both check `$100 >= $80` (passes). Both deduct `$80` and write `$20`. The customer withdrew `$160` but the balance shows `$20` (overdraft double-spend).

### Q22: What is the `FOR UPDATE SKIP LOCKED` clause?
- **Expected Answer:** Instructs PostgreSQL to skip rows currently locked by other transactions and return only unlocked rows matching the query. Used in our `OutboxPublisher` to allow multiple service instances to poll events in parallel without blocking.

### Q23: Why not use Java `synchronized` blocks instead of database locks?
- **Expected Answer:** `synchronized` only synchronizes threads within a **single JVM process**. In a production cloud environment with multiple pods/instances, `synchronized` cannot coordinate across containers. Database locks coordinate across all instances globally.

### Q24: What database isolation level is required for this system?
- **Expected Answer:** PostgreSQL defaults to `READ COMMITTED`. Combined with explicit `SELECT ... FOR UPDATE`, it provides serialized row isolation on accounts while avoiding the serialization rollback overhead of the `SERIALIZABLE` isolation level.

### Q25: How does HikariCP handle connection pooling under high concurrency?
- **Expected Answer:** HikariCP maintains a pool of physical JDBC connections (default 20). When requests exceed 20, threads block in a lock-free queue (`ConcurrentBag`) waiting up to `connection-timeout: 3000ms`.

---

## Category 4: Two-Tier Idempotency & Distributed Systems (Q26 – Q34)

### Q26: What is an Idempotent API?
- **Expected Answer:** An API where making multiple identical requests has the exact same side-effect on the system as making a single request (`f(f(x)) = f(x)`).

### Q27: Explain your Two-Tier Idempotency architecture.
- **Expected Answer:**
  - **Tier 1 (L1 - Redis):** In-memory distributed lock via `SETNX idempotency:lock:<key> <hash> EX 30`. Blocks simultaneous near-instantaneous clicks with `< 1ms` latency.
  - **Tier 2 (L2 - PostgreSQL):** Durable `idempotency_records` table storing payload SHA-256 hash, response body, and status (`IN_PROGRESS`, `COMPLETED`, `FAILED`) with 24-hour expiration.

### Q28: What happens if a client retries a payment with the same idempotency key but alters the amount?
- **Expected Answer:** `IdempotencyKeyService` computes the SHA-256 hash of the incoming request body and compares it to `record.getRequestHash()`. If they differ, it throws `DuplicatePaymentException` (payload tampering).

### Q29: What happens if a duplicate request arrives while the first request is still processing?
- **Expected Answer:** Redis `SETNX` fails (returns `false`) or Postgres status is `IN_PROGRESS`. The service throws `IllegalStateException("A payment with key ... is currently being processed")`, mapped to `409 Conflict`.

### Q30: Why is `Propagation.REQUIRES_NEW` used on `failProcessing()`?
- **Expected Answer:** If the payment transaction throws an exception and rolls back, any database writes in that transaction are erased. `REQUIRES_NEW` suspends the outer transaction and opens an independent physical transaction to persist `status = FAILED`, ensuring the audit trail is preserved.

### Q31: What is the TTL on idempotency records, and why is it needed?
- **Expected Answer:** 24 hours (`payment.idempotency.ttl-hours: 24`). Idempotency is intended to protect against network retries and duplicate user submissions; retaining keys indefinitely would cause unbounded database table growth.

### Q32: What happens if Redis crashes? Does the system stop processing payments?
- **Expected Answer:** If Redis fails, the system can fall back to the PostgreSQL `idempotency_records` table. PostgreSQL provides the durable source of truth, trading minor latency for continuous availability.

### Q33: Why hash the request payload with SHA-256 instead of storing the raw JSON?
- **Expected Answer:** SHA-256 generates a deterministic, fixed 64-character hex string. Comparing 64 characters in SQL B-Tree indexes is orders of magnitude faster than comparing multi-kilobyte JSON strings.

### Q34: What is the Dual-Write Problem?
- **Expected Answer:** When an application attempts to write to a database and publish to a message queue in the same operation. If one succeeds and the other fails, the system enters an inconsistent state.

---

## Category 5: Transactional Outbox Pattern & Apache Kafka (Q35 – Q42)

### Q35: How does the Transactional Outbox Pattern solve the Dual-Write Problem?
- **Expected Answer:** Instead of publishing to Kafka inside the HTTP thread, the domain event is written to the `outbox_events` table inside the **exact same ACID database transaction** as the payment and ledger entries. A separate publisher polls the outbox table and dispatches to Kafka with retries.

### Q36: What Kafka delivery semantics does this project guarantee?
- **Expected Answer:** **At-Least-Once Delivery**. Because network acknowledgments can fail after Kafka writes the message, the outbox publisher may re-send an event. Downstream consumers must be idempotent.

### Q37: Why do we pass `aggregateId` as the Kafka message key?
- **Expected Answer:** Kafka partitions topics by hashing the message key (`hash(key) % numPartitions`). Passing the payment/account ID guarantees all events for a given account arrive on the **exact same partition**, preserving chronological order.

### Q38: What does `acks=all` mean in the Kafka producer configuration?
- **Expected Answer:** The producer waits until the message has been acknowledged by the leader and all in-sync replicas (ISR) before considering the write successful, preventing data loss during broker failover.

### Q39: What is Apache Kafka KRaft mode?
- **Expected Answer:** Kafka Raft metadata mode. Replaces the external Apache Zookeeper ensemble with an internal Raft quorum controller, reducing operational complexity and memory footprint.

### Q40: What happens if Kafka is down when `OutboxPublisher` runs?
- **Expected Answer:** `kafkaTemplate.send().get()` throws an exception. The publisher catches it, logs the error, marks the outbox event as `FAILED`, and increments `retry_count`. The payment in PostgreSQL remains completely safe.

### Q41: How does Kafka consumer group rebalancing work?
- **Expected Answer:** When a new consumer joins or leaves a group, the group coordinator reassigns partitions across consumers. In our tests, we observed `MemberIdRequiredException` during initial join followed by successful partition assignment.

### Q42: What is a Dead-Letter Queue (DLQ) in Kafka?
- **Expected Answer:** A secondary topic where messages that repeatedly fail processing (poison pills) are routed after exceeding max retry attempts, preventing consumer lag from stalling the primary topic.

---

## Category 6: Database, SQL & Flyway Migrations (Q43 – Q52)

### Q43: What is the primary purpose of Flyway?
- **Expected Answer:** To provide version-controlled, automated, deterministic schema migrations. Flyway maintains the `flyway_schema_history` table to ensure every environment runs identical DDL scripts.

### Q44: What happens if `spring.jpa.hibernate.ddl-auto` is set to `create-drop` in production?
- **Expected Answer:** Catastrophic data loss! On restart, Hibernate drops all production tables and recreates empty ones. In production, it must strictly be set to `validate`.

### Q45: Explain the index `CREATE INDEX idx_outbox_status ON outbox_events(status, created_at)`.
- **Expected Answer:** A composite B-Tree index. Because the outbox worker queries `WHERE status = 'PENDING' ORDER BY created_at ASC`, this index satisfies both the filter and the sort without requiring a separate sort operation in memory.

### Q46: What is the N+1 Query Problem in JPA/Hibernate?
- **Expected Answer:** When fetching a parent entity causes N additional queries to fetch associated child entities. In our hexagonal model, we avoid this by using explicit DTO projections and avoid un-needed `@OneToMany` lazy-loading relationships on high-throughput paths.

### Q47: Why are foreign keys essential in a double-entry ledger?
- **Expected Answer:** In `ledger_entries`, `FOREIGN KEY (payment_id) REFERENCES payments(id)` and `FOREIGN KEY (account_id) REFERENCES accounts(id)` guarantee referential integrity; orphan ledger entries can never exist.

### Q48: How does PostgreSQL MVCC handle `VACUUM`?
- **Expected Answer:** Updates create new row versions (dead tuples). `VACUUM` scans pages, reclaims dead tuple storage, and updates the visibility map to prevent transaction ID wraparound.

### Q49: Write a query to find the total debits and credits across the entire ledger.
```sql
SELECT 
    entry_type, 
    SUM(amount) AS total_amount, 
    COUNT(*) AS entry_count 
FROM ledger_entries 
GROUP BY entry_type;
```

### Q50: What is connection leak in Spring Boot?
- **Expected Answer:** When an application borrows a database connection from HikariCP but fails to close it (e.g. unhandled stream or missing transaction completion). Eventually, the pool exhausts and all subsequent requests freeze.

### Q51: What does `open-in-view: false` in `application.yml` do?
- **Expected Answer:** Disables Spring's `OpenEntityManagerInViewFilter`. Prevents database connections from staying open during the view rendering / JSON serialization phase, eliminating lazy-initialization database calls outside the service layer.

### Q52: What is the purpose of PostgreSQL `TIMESTAMPTZ` vs `TIMESTAMP`?
- **Expected Answer:** `TIMESTAMPTZ` converts the timestamp to UTC on storage and handles timezone offsets; standard `TIMESTAMP` discards timezone information, risking confusion across international transactions.

---

## Category 7: Spring Security & RBAC (Q53 – Q60)

### Q53: Explain the Spring Security Filter Chain execution order in your application.
- **Expected Answer:**
  1. `ApiKeyAuthenticationFilter`: Inspects `X-API-KEY`.
  2. `MockJwtAuthenticationFilter`: Inspects `Authorization: Bearer <token>`.
  3. `SecurityContextHolder`: Populated with `Authentication` token containing granted authorities (`ROLE_USER`, `ROLE_FINANCE_ADMIN`).
  4. `AuthorizationFilter`: Verifies route permissions and method annotations (`@PreAuthorize`).

### Q54: What is the purpose of `@EnableMethodSecurity(prePostEnabled = true)`?
- **Expected Answer:** Enables method-level authorization annotations like `@PreAuthorize("hasRole('FINANCE_ADMIN')")` on controller or service methods.

### Q55: Why did you disable CSRF in `SecurityConfig`?
- **Expected Answer:** CSRF (Cross-Site Request Forgery) attacks exploit ambient browser authentication (cookies sent automatically with requests). Because our API is completely stateless (`SessionCreationPolicy.STATELESS`) and uses explicit `Bearer` or `X-API-KEY` headers, CSRF is mathematically impossible.

### Q56: What is the difference between 401 Unauthorized and 403 Forbidden?
- **Expected Answer:**
  - **401 Unauthorized (`AuthenticationEntryPoint`):** The client is not authenticated (missing or invalid credentials).
  - **403 Forbidden (`AccessDeniedHandler`):** The client is authenticated, but lacks the necessary permissions/roles (e.g. standard user calling admin reconciliation).

### Q57: How does `CustomAuthenticationEntryPoint` work?
- **Expected Answer:** Implements `AuthenticationEntryPoint`. When an unauthenticated request attempts to access a protected route, it writes a custom JSON error response (`{"status": 401, "error": "Unauthorized"}`) instead of Spring's default HTML error page.

### Q58: What is the limitation of the `MockJwtAuthenticationFilter` in this repository?
- **Expected Answer:** It parses Base64 claims but does not cryptographically verify the token signature against an asymmetric RSA public key or symmetric HMAC secret.

### Q59: How would you secure this service for production?
- **Expected Answer:** Connect Spring Security to an OAuth2/OIDC identity provider (Okta, Keycloak, Auth0) using `spring-boot-starter-oauth2-resource-server`, validating JWT signatures via JWKS endpoints.

### Q60: What is Principle of Least Privilege?
- **Expected Answer:** Users and services should only be granted the minimum permissions necessary to perform their function (e.g. standard clients can transfer funds, but cannot trigger reconciliation audits).

---

## Category 8: REST API Design & Error Handling (Q61 – Q68)

### Q61: What is the difference between PUT and PATCH?
- **Expected Answer:** `PUT` replaces the entire resource representation; `PATCH` applies a partial update (e.g. `PATCH /api/v1/accounts/{id}/status` updates only the status enum).

### Q62: Why return HTTP 422 Unprocessable Entity for Insufficient Balance?
- **Expected Answer:** HTTP 400 implies malformed syntax. In insufficient balance, the JSON syntax and data types are valid, but the business logic cannot process the instructions; RFC 4918 defines 422 as the standard for semantic business validation failures.

### Q63: How does `@Valid` interact with `@RestController`?
- **Expected Answer:** When Spring MVC deserializes JSON into a DTO annotated with `@Valid`, it executes Jakarta Validation constraints (`@NotNull`, `@Positive`). If validation fails, it throws `MethodArgumentNotValidException`, caught by `GlobalExceptionHandler`.

### Q64: What is the advantage of using `@RestControllerAdvice`?
- **Expected Answer:** Centralizes exception handling across all controllers. Prevents repetitive `try/catch` boilerplate in controllers and ensures all API clients receive uniform error envelopes.

### Q65: What is OpenAPI / Swagger, and how is it configured here?
- **Expected Answer:** OpenAPI 3 is an API description specification. Configured via `springdoc-openapi-starter-webmvc-ui` and `OpenApiConfig.java`, exposing interactive documentation at `/swagger-ui.html`.

### Q66: Why use UUIDs in URLs instead of sequential IDs (e.g. `/payments/1001`)?
- **Expected Answer:** Sequential IDs leak business volume (competitors can see transaction volume) and enable enumeration attacks (scraping records by incrementing numbers). UUIDs are cryptographically random.

### Q67: What HTTP status code should a successful refund return?
- **Expected Answer:** `HTTP 200 OK` (returning the updated payment/refund response) or `HTTP 201 Created` if treating the refund as a distinct newly generated transaction resource.

### Q68: What is RFC 7807 (Problem Details for HTTP APIs)?
- **Expected Answer:** An IETF standard defining a standardized JSON format for HTTP API error responses (`type`, `title`, `status`, `detail`, `instance`).

---

## Category 9: Observability, Metrics & Actuator (Q69 – Q76)

### Q69: What is Spring Boot Actuator?
- **Expected Answer:** Production-ready management endpoints providing telemetry on health, metrics, environment, and thread dumps.

### Q70: How does `LedgerHealthIndicator` work?
- **Expected Answer:** A custom Spring `HealthIndicator` bean. When `/actuator/health` is queried, it runs a fast invariant check comparing total balances to ledger sums. If discrepancies exist, it marks health as `DOWN`.

### Q71: What is Micrometer and Prometheus integration?
- **Expected Answer:** Micrometer is a vendor-neutral metrics facade (like SLF4J for metrics). With `micrometer-registry-prometheus`, it formats JVM, GC, and HTTP latency metrics into Prometheus scrape format at `/actuator/prometheus`.

### Q72: What is the RED method in microservice monitoring?
- **Expected Answer:** Rate (requests per second), Errors (failed requests per second), Duration (latency distribution / percentiles).

### Q73: What is Correlation ID and why is `CorrelationIdFilter` important?
- **Expected Answer:** A unique identifier (`X-Correlation-ID`) attached to an incoming HTTP request and stored in SLF4J's `MDC` (Mapped Diagnostic Context). Enables tracing a single transaction across distributed logs across microservices.

### Q74: What is p99 latency and why is it more important than average latency?
- **Expected Answer:** The latency experienced by the slowest 1% of requests. Average latency masks severe latency spikes caused by garbage collection pauses or database lock waits.

### Q75: How do you monitor Kafka consumer lag?
- **Expected Answer:** By monitoring the difference between the partition log end offset (latest produced message) and the consumer group's committed offset using Kafka Actuator metrics or Kafka UI.

### Q76: What is a Health Check vs a Liveness/Readiness probe?
- **Expected Answer:**
  - **Liveness:** Is the JVM alive? (If dead, Kubernetes restarts the pod).
  - **Readiness:** Can the pod accept incoming traffic? (If database connection pool is full, traffic is temporarily stopped from routing to this pod).

---

## Category 10: Testing, JUnit & Testcontainers (Q77 – Q86)

### Q77: Why did you use Testcontainers instead of H2 for integration testing?
- **Expected Answer:** H2 does not implement PostgreSQL locking semantics (`SELECT ... FOR UPDATE`, `SKIP LOCKED`), native UUID types, or actual socket timeouts. Testcontainers runs real PostgreSQL and Redis containers, ensuring tests accurately reflect production.

### Q78: Explain the Testcontainers Singleton Pattern used in `AbstractIntegrationTest`.
- **Expected Answer:** Static container fields started in a `static {}` block without `@Testcontainers` or `@Container` annotations. Prevents JUnit Jupiter from stopping containers between test classes, eliminating `Connection refused` errors.

### Q79: How does `EmbeddedKafka` work in tests?
- **Expected Answer:** Spins up an in-process Apache Kafka broker during test execution, allowing tests to verify real message production and consumption without requiring an external Kafka cluster.

### Q80: What is Awaitility and why is it used in `FailureRecoveryTest`?
- **Expected Answer:** A DSL for asynchronous systems testing. Used to poll and assert that the asynchronous `OutboxPublisher` eventually transitions outbox records from `PENDING` to `PUBLISHED` within 10 seconds.

### Q81: What is the purpose of `@DynamicPropertySource`?
- **Expected Answer:** Registers dynamic properties into the Spring Environment at runtime (e.g. mapping random ephemeral container ports `POSTGRES.getJdbcUrl()` into `spring.datasource.url`).

### Q82: How did you verify that concurrent requests don't cause lost updates?
- **Expected Answer:** In `ConcurrentPaymentTest`, 20 parallel threads execute `$10` debits from a `$1000` account using an `ExecutorService` and `CountDownLatch`. At the end, the test asserts that `source + destination == 1000.00`.

### Q83: What is a `CountDownLatch` and why is it used in concurrency testing?
- **Expected Answer:** A synchronization barrier. A `startLatch` with count 1 blocks all 20 worker threads until `countDown()` is called, ensuring all threads strike the API at the **exact same millisecond**.

### Q84: What is the difference between `@Mock` and `@MockBean`?
- **Expected Answer:**
  - `@Mock`: Mockito mock; plain Java object used in pure unit tests.
  - `@MockBean`: Spring Boot mock; replaces an existing bean in the Spring `ApplicationContext` with a Mockito mock.

### Q85: What is test isolation and how was state leakage fixed in `IdempotencyTest`?
- **Expected Answer:** Tests should not depend on data from other tests. We fixed line 63 of `IdempotencyTest` by filtering count assertions specifically for that test's `idempotencyKey` rather than assuming a globally empty table.

### Q86: What is mutation testing?
- **Expected Answer:** A testing technique where code statements are intentionally mutated (e.g. changing `>=` to `>`) to verify if the test suite catches the mutation and fails.

---

## Category 11: Scalability, Caching & Performance (Q87 – Q94)

### Q87: What is the maximum TPS of this service on a single node?
- **Expected Answer:** Approximately 200–400 TPS, constrained by HikariCP connection pool size (20 connections) and PostgreSQL disk sync latency on row locks.

### Q88: How would you scale this application to 50,000 TPS?
- **Expected Answer:**
  1. Shard PostgreSQL databases horizontally by `hash(account_id)`.
  2. Replace outbox SQL polling with Debezium Change Data Capture (CDC) streaming WAL logs to Kafka.
  3. Deploy 30+ stateless Spring Boot pods behind AWS ALB.
  4. Shard Redis into a multi-node cluster.

### Q89: Why is Redis faster than PostgreSQL for in-flight idempotency locks?
- **Expected Answer:** Redis operates completely in-memory on a single-threaded event loop, avoiding disk I/O, relational table locks, and MVCC overhead, executing `SETNX` in < 1ms.

### Q90: What is the Thundering Herd Problem in caching?
- **Expected Answer:** When a popular cached key expires, and thousands of concurrent requests miss the cache simultaneously, hitting the underlying database at once. Solved via distributed mutex locks or probabilistic early expiration.

### Q91: What is Database Connection Pool Starvation?
- **Expected Answer:** When all database connections in HikariCP are occupied and waiting on external calls (e.g. an HTTP API or slow lock), causing all incoming requests to freeze and time out.

### Q92: Why did you set `ORDER BY created_at ASC LIMIT :batchSize` in the outbox query?
- **Expected Answer:** Limits the memory footprint of each query batch and ensures older events are published before newer events (FIFO ordering).

### Q93: What is JVM Garbage Collection Pause (Stop-the-World)?
- **Expected Answer:** When the JVM pauses application execution threads to collect unreachable objects in the heap. In Dockerfile, we configured `-XX:+UseG1GC` (Garbage-First Collector) to maintain sub-10ms pause times.

### Q94: How does Database Sharding handle cross-shard transactions?
- **Expected Answer:** Using a Two-Phase Commit (2PC) protocol or a distributed Saga pattern with compensating reversing transactions.

---

## Category 12: Architecture Trade-Offs & Self-Defense (Q95 – Q102)

### Q95: What is the biggest weakness or limitation of your current implementation?
- **Expected Answer:**  
  *"The biggest architectural trade-off is polling the outbox table via SQL every 500ms. While simple and zero-dependency, under thousands of TPS, continuous polling creates unnecessary database read load and introduces up to 500ms event delivery latency. In a high-scale production evolution, I would replace polling with Debezium CDC reading PostgreSQL Write-Ahead Logs."*

### Q96: Why did you use Spring Boot instead of Go or Rust?
- **Expected Answer:** Spring Boot offers the most mature declarative transaction management (`@Transactional`), battle-tested JPA ORM, enterprise security ecosystems, and rapid development speed, making it the dominant backend choice across tier-1 banks.

### Q97: What would you change if you had to rebuild this project tomorrow?
- **Expected Answer:**
  1. Implement true RS256 JWT cryptographic signature validation using OAuth2 Resource Server.
  2. Add multi-currency exchange rate conversion service for foreign currency transfers.
  3. Introduce database read replicas with `@Transactional(readOnly = true)` routing.

### Q98: Why not use a distributed transaction manager (JTA / 2PC)?
- **Expected Answer:** Two-Phase Commit is blocking, slow, prone to coordinator failure, and scales poorly. The Transactional Outbox Pattern provides reliable eventual consistency without distributed 2PC locks.

### Q99: How do you justify the memory overhead of JVM inside Docker containers?
- **Expected Answer:** Using Alpine-based Eclipse Temurin JRE 17 with `-XX:MaxRAMPercentage=75.0` keeps total container memory under 512MB, easily fitting into modern cloud container density targets.

### Q100: How do you verify that an AI-assisted codebase is secure and correct?
- **Expected Answer:**  
  *"By enforcing strict Test-Driven Development with real Testcontainers, writing load tests with k6, performing static code analysis, auditing database locks at the SQL engine level, and validating failure modes like network partitions and lock timeouts rather than trusting code syntax alone."*

### Q101: What is the difference between a Distributed Lock and a Database Lock?
- **Expected Answer:** A distributed lock (Redis Redlock/SETNX) coordinates application-level intent across microservice pods without touching database transactions. A database lock (`FOR UPDATE`) locks physical tuples inside the relational storage engine.

### Q102: How do you explain this project to a non-technical stakeholder?
- **Expected Answer:** *"Think of it as a digital bank teller that never makes a math mistake. Every time money moves, it writes down who gave it and who got it in an indelible ink book. If you accidentally tap your card twice, it recognizes the duplicate and only charges you once."*
