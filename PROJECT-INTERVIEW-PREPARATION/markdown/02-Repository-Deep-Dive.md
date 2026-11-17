# 02. Repository Deep Dive & File-by-File Forensic Map

> **Scope:** Detailed architectural audit of every directory, configuration, package, and source file in the repository.

---

## 1. Complete Repository Architecture Map

```
D:\PROJECTS\RESUME PROJECTS\PAYMENT LEDGER MAIN\
├── .github/
│   └── workflows/
│       ├── ci.yml                           # GitHub Actions: Maven Build, Unit & Integration Tests, Docker image build
│       └── concurrency-and-load-tests.yml   # CI job running k6 load test against live Postgres/Redis service
├── load-tests/
│   └── k6-payment-transfer.js               # Grafana k6 performance testing script (50 concurrent VUs, latency SLAs)
├── src/
│   ├── main/
│   │   ├── java/com/aman/payment/
│   │   │   ├── PaymentApplication.java       # Spring Boot main class, @EnableScheduling, component scan root
│   │   │   ├── application/                 # Hexagonal Application Layer (Use Cases & Business Orchestration)
│   │   │   │   ├── port/
│   │   │   │   │   ├── in/                  # Inbound Ports (Driving: Primary interfaces implemented by services)
│   │   │   │   │   │   ├── GetPaymentUseCase.java
│   │   │   │   │   │   ├── ManageAccountUseCase.java
│   │   │   │   │   │   ├── ProcessPaymentUseCase.java
│   │   │   │   │   │   ├── QueryLedgerUseCase.java
│   │   │   │   │   │   └── RefundPaymentUseCase.java
│   │   │   │   │   └── out/                 # Outbound Ports (Driven: Secondary interfaces implemented by adapters)
│   │   │   │   │       ├── AccountRepositoryPort.java
│   │   │   │   │       ├── IdempotencyPort.java
│   │   │   │   │       ├── LedgerEntryRepositoryPort.java
│   │   │   │   │       ├── PaymentEventPublisherPort.java
│   │   │   │   │       └── PaymentRepositoryPort.java
│   │   │   │   └── service/                 # Application Services orchestrating domain models and ports
│   │   │   │       ├── AccountApplicationService.java
│   │   │   │       ├── LedgerApplicationService.java
│   │   │   │       └── PaymentApplicationService.java
│   │   │   ├── domain/                      # Pure Domain Core (Zero Spring/DB dependencies)
│   │   │   │   ├── exception/               # Rich Domain Exceptions
│   │   │   │   │   ├── AccountInactiveException.java
│   │   │   │   │   ├── CurrencyMismatchException.java
│   │   │   │   │   ├── DomainException.java
│   │   │   │   │   ├── DuplicatePaymentException.java
│   │   │   │   │   ├── InsufficientBalanceException.java
│   │   │   │   │   ├── InvalidAccountException.java
│   │   │   │   │   ├── InvalidPaymentStateException.java
│   │   │   │   │   ├── LedgerImbalanceException.java
│   │   │   │   │   └── PaymentNotFoundException.java
│   │   │   │   └── model/                   # Domain Entities, Value Objects, and Invariant Engines
│   │   │   │       ├── Account.java
│   │   │   │       ├── AccountStatus.java
│   │   │   │       ├── DoubleEntryLedger.java
│   │   │   │       ├── EntryType.java
│   │   │   │       ├── LedgerEntry.java
│   │   │   │       ├── Money.java
│   │   │   │       ├── Payment.java
│   │   │   │       └── PaymentStatus.java
│   │   │   ├── infrastructure/              # Infrastructure Layer (Secondary Adapters, Framework Integrations)
│   │   │   │   ├── config/                  # Spring Configuration Beans
│   │   │   │   │   ├── CorrelationIdFilter.java
│   │   │   │   │   ├── DatabaseConfig.java
│   │   │   │   │   ├── LedgerHealthIndicator.java
│   │   │   │   │   ├── OpenApiConfig.java
│   │   │   │   │   └── RedisConfig.java
│   │   │   │   ├── idempotency/             # Redis + Postgres Idempotency Engine
│   │   │   │   │   ├── IdempotencyAdapter.java
│   │   │   │   │   └── IdempotencyKeyService.java
│   │   │   │   ├── messaging/               # Kafka & Outbox Relay
│   │   │   │   │   ├── kafka/
│   │   │   │   │   │   ├── KafkaConfig.java
│   │   │   │   │   │   ├── KafkaPaymentEventProducer.java
│   │   │   │   │   │   ├── PaymentEvent.java
│   │   │   │   │   │   └── PaymentEventConsumer.java
│   │   │   │   │   └── outbox/
│   │   │   │   │       ├── OutboxPublisher.java
│   │   │   │   │       └── OutboxService.java
│   │   │   │   ├── persistence/             # Spring Data JPA Entities, Mappers & Repositories
│   │   │   │   │   ├── adapter/
│   │   │   │   │   │   ├── AccountRepositoryAdapter.java
│   │   │   │   │   │   ├── LedgerEntryRepositoryAdapter.java
│   │   │   │   │   │   └── PaymentRepositoryAdapter.java
│   │   │   │   │   ├── entity/
│   │   │   │   │   │   ├── AccountJpaEntity.java
│   │   │   │   │   │   ├── IdempotencyRecordJpaEntity.java
│   │   │   │   │   │   ├── LedgerEntryJpaEntity.java
│   │   │   │   │   │   ├── OutboxEventJpaEntity.java
│   │   │   │   │   │   └── PaymentJpaEntity.java
│   │   │   │   │   ├── mapper/
│   │   │   │   │   │   ├── AccountJpaMapper.java
│   │   │   │   │   │   ├── LedgerEntryJpaMapper.java
│   │   │   │   │   │   └── PaymentJpaMapper.java
│   │   │   │   │   └── repository/
│   │   │   │   │       ├── SpringDataAccountRepository.java
│   │   │   │   │       ├── SpringDataIdempotencyRepository.java
│   │   │   │   │       ├── SpringDataLedgerEntryRepository.java
│   │   │   │   │       ├── SpringDataOutboxEventRepository.java
│   │   │   │   │       └── SpringDataPaymentRepository.java
│   │   │   │   └── security/                # Spring Security 6 Filter Chain & Handlers
│   │   │   │       ├── ApiKeyAuthenticationFilter.java
│   │   │   │       ├── CustomAccessDeniedHandler.java
│   │   │   │       ├── CustomAuthenticationEntryPoint.java
│   │   │   │       ├── MockJwtAuthenticationFilter.java
│   │   │   │       └── SecurityConfig.java
│   │   │   └── presentation/                # Primary Driving Adapters (REST Controllers)
│   │   │       └── v1/
│   │   │           ├── AccountController.java
│   │   │           ├── LedgerController.java
│   │   │           ├── PaymentController.java
│   │   │           ├── dto/
│   │   │           │   ├── AccountResponse.java
│   │   │           │   ├── FundRequest.java
│   │   │           │   ├── LedgerEntryResponse.java
│   │   │           │   ├── OpenAccountRequest.java
│   │   │           │   ├── PaymentRequest.java
│   │   │           │   ├── PaymentResponse.java
│   │   │           │   ├── RefundRequest.java
│   │   │           │   └── StatusUpdateRequest.java
│   │   │           └── exception/
│   │   │               └── GlobalExceptionHandler.java
│   │   └── resources/
│   │       ├── application.yml              # Primary production/local YAML configuration
│   │       ├── schema.sql                   # Reference ANSI SQL schema
│   │       ├── db/migration/
│   │       │   └── V1__init_schema.sql      # Flyway migration script
│   │       └── static/                      # Web Dashboard Frontend
│   │           ├── index.html               # Semantic HTML5 Single-Page Dashboard
│   │           ├── style.css                # Dark-mode FinTech CSS design system
│   │           └── app.js                   # Vanilla JS frontend client & event handler
│   └── test/
│       ├── java/com/aman/payment/test/
│       │   ├── AbstractIntegrationTest.java # Base Testcontainers class (Postgres, Redis, Embedded Kafka)
│       │   ├── ConcurrentPaymentTest.java   # 20-thread concurrency test (lost updates & deadlocks)
│       │   ├── DoubleEntryLedgerTest.java   # Pure domain specification for debit/credit balancing
│       │   ├── FailureRecoveryTest.java     # Outbox publishing recovery and failed payment rollbacks
│       │   ├── IdempotencyTest.java         # Duplicate requests and concurrent key collision tests
│       │   ├── LedgerInvariantTest.java     # Balance consistency and reconciliation audit tests
│       │   ├── MoneyTest.java               # Value object precision, math, and currency match tests
│       │   └── SecurityTest.java            # 401 unauthorized, 403 forbidden, and public Swagger checks
│       └── resources/
│           ├── application-test.yml         # Test profile configuration
│           └── docker-java.properties       # Docker API 1.44 configuration for Testcontainers
├── .env.example                             # Template environment variables
├── .gitignore                               # Git exclusion rules
├── Dockerfile                               # Multi-stage production container build
├── docker-compose.yml                       # Multi-container orchestration (App, Postgres, Redis, Kafka, UI)
├── pom.xml                                  # Maven dependencies, plugins, and build specifications
└── README.md                                # High-level project documentation
```

---

## 2. Forensic Analysis of Key Directories and Files

### A. Root Build & Infrastructure Configurations

#### 1. `pom.xml`
- **What it does:** Declares project coordinates (`com.aman:payment-ledger:1.0.0`), Spring Boot parent (`3.2.5`), Java version (`17`), dependencies, and Maven plugins.
- **Why it exists:** Provides deterministic, repeatable dependency resolution and compilation management.
- **Key Dependencies:**
  - `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`.
  - `spring-boot-starter-data-redis`, `spring-kafka`, `postgresql`, `flyway-core`.
  - `springdoc-openapi-starter-webmvc-ui` (v2.5.0), `micrometer-registry-prometheus`.
  - Test: `testcontainers:postgresql:1.20.4`, `spring-kafka-test`, `awaitility:4.2.1`.
- **What would break if removed:** The project cannot compile or resolve libraries; Maven build fails immediately.
- **Interview Questions:**
  - *"Why did you pin Testcontainers to 1.20.4 instead of letting Spring Boot manage it?"* (Answer: Docker 29+ compatibility requires newer docker-java clients).
  - *"What is the purpose of the `<optional>true</optional>` tag on Lombok?"* (Answer: Prevents Lombok bytecode annotations from leaking transitively to downstream consumers of this JAR).

#### 2. `docker-compose.yml`
- **What it does:** Spawns 5 containerized services with network bridging:
  1. `postgres` (port 5433 -> container 5432) with persistent volume `pgdata`.
  2. `redis` (port 6380 -> container 6379) with persistent volume `redisdata`.
  3. `kafka` (port 9092) in KRaft mode (no Zookeeper!) with volume `kafkadata`.
  4. `kafka-ui` (port 8085 -> container 8080) pointing to `kafka:9092`.
  5. `app` (port 8081 -> container 8080) under profile `app`.
- **Why it exists:** Enables instant, 1-command local development setup without installing PostgreSQL, Redis, or Kafka binaries on the host OS.
- **What would break if removed:** Developers must manually install, configure, and run separate database, cache, and message broker services with exact credentials.
- **Interview Questions:**
  - *"Why are the host ports mapped to 5433 and 6380 instead of 5432 and 6379?"* (Answer: Avoids collisions with standard local installations of Postgres and Redis running on the developer's laptop).
  - *"How does Docker Compose ensure the app only starts after Postgres is ready?"* (Answer: Using `depends_on` with `condition: service_healthy` checking `pg_isready`).

#### 3. `Dockerfile`
- **What it does:** Executes a multi-stage container build:
  - **Stage 1 (Builder):** Uses `maven:3.9.6-eclipse-temurin-17`, runs `dependency:go-offline` for caching, then packages the JAR.
  - **Stage 2 (Runtime):** Uses minimal `eclipse-temurin:17-jre-alpine`, creates a dedicated non-root user `appuser` (security best practice), copies `app.jar`, and starts Java with `-XX:+UseG1GC -XX:MaxRAMPercentage=75.0`.
- **Why it exists:** Produces a lightweight, secure production-grade container image (< 200MB) without bundling Maven or source code into the production artifact.
- **What would break if removed:** Cloud container platforms (AWS ECS, Kubernetes) cannot deploy the application.
- **Interview Questions:**
  - *"Why run the container under a non-root user (`appuser`)?"* (Answer: Container escape vulnerability mitigation: if the JVM process is compromised, the attacker does not have root privileges on the underlying host kernel).
  - *"What does `-XX:MaxRAMPercentage=75.0` do?"* (Answer: Informs the JVM to restrict heap sizing to 75% of the container's cgroup memory limit, leaving 25% for Metaspace, thread stacks, and OS overhead to prevent OOMKilled errors).

---

### B. Core Domain Layer (`com.aman.payment.domain`)

#### 1. `domain.model.Money`
- **What it does:** An immutable Value Object representing financial amounts. Wraps a `BigDecimal` scaled to 4 decimal places (`RoundingMode.HALF_EVEN`) and a 3-letter currency code (ISO 4217).
- **Methods:** `add(Money)`, `subtract(Money)`, `isGreaterThanOrEqualTo(Money)`, `isZero()`, `isPositive()`.
- **Why it exists:** In financial applications, using `double` or `float` causes floating-point rounding errors (e.g. `0.1 + 0.2 = 0.30000000000000004`). `Money` guarantees exact arithmetic. Enforces currency matching: attempting to add `USD` to `EUR` throws `CurrencyMismatchException`.
- **What would break if removed:** Financial drift occurs immediately due to binary floating-point representation errors.

#### 2. `domain.model.Account`
- **What it does:** Rich Domain Entity encapsulating customer balance, account status (`ACTIVE`, `FROZEN`, `CLOSED`), and system reserve flags.
- **Key Methods:**
  - `debit(Money)`: Validates active status and currency, asserts sufficient funds (unless system reserve), and deducts balance.
  - `credit(Money)`: Validates active status and currency, and increases balance.
  - `freeze()`, `activate()`, `close()`: Enforces status transition state machine.
- **Why it exists:** Prevents the "Anemic Domain Model" anti-pattern. Business invariants are enforced inside the entity itself rather than scattered across services.
- **Interview Questions:**
  - *"Why does the system account bypass the sufficient balance check?"* (Answer: System reserve accounts represent the central liquidity pool; they can maintain negative or unlimited float to facilitate fiat deposits into the ecosystem).

#### 3. `domain.model.DoubleEntryLedger`
- **What it does:** The invariant engine for double-entry bookkeeping.
- **Method:** `postTransfer(UUID paymentId, Account source, Account destination, Money amount) -> PostingResult`
- **Execution Flow:**
  1. Validates that source and destination are different accounts.
  2. Executes `source.debit(amount)`.
  3. Executes `destination.credit(amount)`.
  4. Generates an immutable `DEBIT` `LedgerEntry` for source with `balanceAfter`.
  5. Generates an immutable `CREDIT` `LedgerEntry` for destination with `balanceAfter`.
  6. Validates invariant: `debitEntry.amount == creditEntry.amount`.
  7. Returns `PostingResult` containing updated accounts and both entries.
- **Interview Questions:**
  - *"Why is `DoubleEntryLedger` a domain class rather than a Spring Service?"* (Answer: It contains zero framework or database dependencies, allowing it to be tested in pure unit tests executing in under 5 milliseconds).

---

### C. Application Layer (`com.aman.payment.application`)

#### 1. `application.service.PaymentApplicationService`
- **What it does:** The primary orchestrator implementing `ProcessPaymentUseCase`, `GetPaymentUseCase`, and `RefundPaymentUseCase`.
- **Dependencies:** `PaymentRepositoryPort`, `AccountRepositoryPort`, `LedgerEntryRepositoryPort`, `PaymentEventPublisherPort`, `IdempotencyPort`, `DoubleEntryLedger`.
- **Critical Algorithm: Deadlock-Free Concurrency & Idempotency Orchestration:**
  ```java
  // 1. Evaluate Idempotency in Redis & Postgres
  Decision decision = idempotencyPort.evaluate(key, payload);
  if (decision == RETURN_CACHED) return cachedPayment;
  if (decision == CONFLICT_IN_FLIGHT) throw 409 Conflict;

  // 2. Deadlock Avoidance: Lock in Global UUID Order
  UUID firstId = sourceId.compareTo(destId) <= 0 ? sourceId : destId;
  UUID secondId = firstId.equals(sourceId) ? destId : sourceId;

  Account first = accountRepository.findByIdForUpdate(firstId);
  Account second = accountRepository.findByIdForUpdate(secondId);

  // 3. Post Double-Entry Transfer & Persist
  PostingResult result = doubleEntryLedger.postTransfer(paymentId, src, dst, amount);
  // Persist accounts, ledger entries, payment, and publish outbox event atomically
  ```
- **What would break if removed:** Payments cannot be processed, verified, or audited.
- **Interview Questions:**
  - *"Why did you inject `@Lazy PaymentApplicationService self`?"* (Answer: To bypass Spring's self-invocation limitation. When `processPayment` calls `executeInTransaction`, calling `this.executeInTransaction()` skips the CGLIB dynamic proxy, disabling `@Transactional`. Calling `self.executeInTransaction()` ensures Spring's transaction interceptor executes).

#### 2. `application.service.LedgerApplicationService`
- **What it does:** Audits the ledger and calculates balances.
- **Key Method:** `reconcileAll() -> List<Discrepancy>`
- **Execution Flow:**
  1. Fetches all accounts.
  2. For each account, calculates expected balance: `SUM(credits) - SUM(debits)`.
  3. Compares expected balance against `Account.balance`.
  4. If mismatch is detected, records a `Discrepancy(ACCOUNT_BALANCE_DRIFT)`.
  5. For all payments, verifies `SUM(debits) == SUM(credits)`.
- **Interview Questions:**
  - *"Why not auto-correct discrepancies automatically?"* (Answer: In banking, financial software must NEVER silently mutate balances. Discrepancies must trigger alerts for human compliance audit and be resolved via audited correcting entries).

---

### D. Infrastructure Layer (`com.aman.payment.infrastructure`)

#### 1. `infrastructure.idempotency.IdempotencyKeyService`
- **What it does:** Two-tier idempotency coordinator.
- **Execution Flow:**
  - **Tier 1 (Redis):** Executes `SETNX idempotency:lock:<key> <hash> EX 30`. If lock fails, another thread is processing -> returns `IN_FLIGHT`.
  - **Tier 2 (Postgres):** Checks `idempotency_records` table. If found and `COMPLETED`, returns cached JSON body. If hash differs, throws `DuplicatePaymentException` (payload tampering).
- **Transaction Propagation Nuance:**
  - `beginProcessing()` uses `Propagation.MANDATORY` (must participate in payment transaction).
  - `failProcessing()` uses `Propagation.REQUIRES_NEW` (runs in a separate physical transaction so that when the outer payment transaction rolls back, the failure state is still recorded and the Redis lock is freed).

#### 2. `infrastructure.messaging.outbox.OutboxPublisher`
- **What it does:** Scheduled worker polling `outbox_events` table every 500ms.
- **Query:** `SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 100 FOR UPDATE SKIP LOCKED`
- **What it does:** Publishes each event to Kafka topic `payment.events`. On success, marks `PUBLISHED`. On failure, increments `retry_count` and marks `FAILED`.
- **Interview Questions:**
  - *"Why `SKIP LOCKED`?"* (Answer: Enables horizontal scaling. Multiple microservice pods can poll the outbox table simultaneously without blocking or waiting on each other).

#### 3. `infrastructure.security.SecurityConfig`
- **What it does:** Configures Spring Security 6 stateless filter chain.
- **Rules:**
  - Public: `/`, `/index.html`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`, `/actuator/prometheus`.
  - Admin: `/api/v1/ledger/reconcile` requires `ROLE_FINANCE_ADMIN`.
  - Authenticated: All other `/api/**` routes require authentication via `ApiKeyAuthenticationFilter` or `MockJwtAuthenticationFilter`.
