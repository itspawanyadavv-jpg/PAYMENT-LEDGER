# 01. Project Overview & Forensic Implementation Audit

> **Service:** Production-Grade Double-Entry Payment Ledger Microservice  
> **Repository:** `D:\PROJECTS\RESUME PROJECTS\PAYMENT LEDGER MAIN`  
> **Target Role:** SDE-1 / Java Backend Engineer (FinTech / High-Throughput Distributed Systems)  
> **Author & Candidate:** PAWAN YADAV (`itspawanyadavv@gmail.com`)

---

## 1. Executive Summary & Problem Space

In modern financial technology (banking cores, payment gateways, crypto exchanges, digital wallets like PayPal, Stripe, and Razorpay), **data integrity is non-negotiable**. A single lost update, race condition, or dual-write failure can result in ledger drift, double spending, regulatory fines, and catastrophic financial loss.

Most junior backend candidate portfolios contain trivial CRUD applications (e-commerce carts, blog apps, task managers) that rely on naive database updates like:
```sql
-- NAIVE JUNIOR PATTERN (Fatal in FinTech):
UPDATE accounts SET balance = balance - 100 WHERE id = 'alice';
UPDATE accounts SET balance = balance + 100 WHERE id = 'bob';
```
This naive approach suffers from four fatal flaws:
1. **Lost Updates & Race Conditions:** Two simultaneous requests can read the same balance and overwrite each other.
2. **Lack of Auditability:** If a balance is corrupted, there is no mathematical proof of which transaction caused the discrepancy.
3. **Dual-Write Vulnerability:** If the database commits but the notification service or event broker (Kafka) fails, downstream systems desynchronize.
4. **Idempotency Failures:** If a user double-clicks "Pay" or network retries occur, the user is charged twice.

### The Solution: This Payment Ledger Microservice
This project is an enterprise-grade financial ledger engine built in **Java 17** and **Spring Boot 3.2.5** that models real-world banking infrastructure:
- **Strict Double-Entry Bookkeeping:** Every transaction creates immutable, balanced `DEBIT` and `CREDIT` legs. Total money is invariant.
- **Two-Tier Idempotency:** Sub-millisecond distributed in-flight locking via **Redis 7** combined with durable payload-hash verification in **PostgreSQL 15**.
- **Deadlock-Free Row-Level Locking:** Deterministic lock acquisition ordered by account UUID eliminating circular wait conditions during concurrent bidirectional transfers.
- **Transactional Outbox Pattern:** Atomic DB insertion of domain events published asynchronously to **Apache Kafka 3.7** using PostgreSQL `FOR UPDATE SKIP LOCKED`.
- **Continuous Audit & Reconciliation:** Self-healing invariant checking verifying that denormalized account balances exactly equal the sum of their immutable ledger entries.

---

## 2. Forensic Project Inventory & Implementation Status

A rigorous audit was conducted across every file, entity, service, configuration, and test in this repository. Below is the verified status of every architectural component:

| Feature / Subsystem | Implementation File(s) | Verified Status | Forensic Engineering Notes |
|---|---|---|---|
| **Hexagonal Domain Core** | `domain/model/Money.java`<br>`domain/model/Account.java`<br>`domain/model/DoubleEntryLedger.java`<br>`domain/model/Payment.java` | **Fully Implemented** | Pure Java without framework imports. Money uses `BigDecimal` with 4 decimal places and strict currency matching. Account status machine (`ACTIVE`, `FROZEN`, `CLOSED`). |
| **Double-Entry Invariant** | `domain/model/DoubleEntryLedger.java`<br>`application/service/LedgerApplicationService.java` | **Fully Implemented** | Guaranteed: `debitAmount == creditAmount`. For transfers, funds are debited from source and credited to destination. For deposits/withdrawals, balanced against `SYSTEM_RESERVE` accounts. |
| **Deadlock Avoidance Locking** | `application/service/PaymentApplicationService.java` (`executeInTransaction`) | **Fully Implemented** | Deterministic UUID comparison (`firstId.compareTo(secondId) <= 0`). Account locks acquired in strict global order via `SELECT ... FOR UPDATE`. |
| **Two-Tier Idempotency (L1/L2)** | `infrastructure/idempotency/IdempotencyKeyService.java`<br>`infrastructure/idempotency/IdempotencyAdapter.java` | **Fully Implemented** | L1: Redis `SETNX` with 30s lock TTL. L2: PostgreSQL `idempotency_records` table storing SHA-256 payload hash, response body, and status (`IN_PROGRESS`, `COMPLETED`, `FAILED`). |
| **Transactional Outbox Relay** | `infrastructure/messaging/outbox/OutboxPublisher.java`<br>`infrastructure/persistence/repository/SpringDataOutboxEventRepository.java` | **Fully Implemented** | Events inserted in payment ACID transaction. Publisher polls using native SQL `SELECT * FOR UPDATE SKIP LOCKED` every 500ms and sends to Kafka topic `payment.events`. |
| **Kafka Event Pipeline** | `infrastructure/messaging/kafka/KafkaPaymentEventProducer.java`<br>`infrastructure/messaging/kafka/PaymentEventConsumer.java` | **Fully Implemented** | Idempotent producer (`enable.idempotence=true`, `acks=all`). Consumer listens to `payment.events` with manual/auto offset handling. |
| **Continuous Reconciliation** | `application/service/LedgerApplicationService.java` (`reconcileAll`)<br>`infrastructure/config/LedgerHealthIndicator.java` | **Fully Implemented** | Compares `Account.balance` against `SUM(credits) - SUM(debits)` from `ledger_entries`. Plugs into Spring Actuator `/actuator/health` to trigger `DOWN` status on drift. |
| **Security & RBAC** | `infrastructure/security/SecurityConfig.java`<br>`infrastructure/security/ApiKeyAuthenticationFilter.java`<br>`infrastructure/security/MockJwtAuthenticationFilter.java` | **Partially Implemented (Mock Auth)** | Stateless security filter chain exists with method security (`@PreAuthorize`). API keys (`X-API-KEY`) and Bearer tokens are supported, but token signature verification is mock-based (no cryptographic HMAC/RSA validation). |
| **REST API Layer** | `presentation/v1/PaymentController.java`<br>`presentation/v1/AccountController.java`<br>`presentation/v1/LedgerController.java` | **Fully Implemented** | RESTful resource conventions, DTO validation via Jakarta Validation (`@Valid`, `@NotNull`, `@Positive`), OpenAPI/Swagger 3 annotations. |
| **Global Error Handling** | `presentation/v1/exception/GlobalExceptionHandler.java` | **Fully Implemented** | `@RestControllerAdvice` mapping 14 distinct domain & framework exceptions to standardized RFC-7807 style JSON payloads with correct HTTP status codes (400, 401, 403, 404, 409, 422, 500). |
| **Web Dashboard UI** | `src/main/resources/static/index.html`<br>`src/main/resources/static/app.js`<br>`src/main/resources/static/style.css` | **Fully Implemented (with 1 Auth Nuance)** | Responsive dashboard featuring KPI metrics, account creation modal, liquidity top-up/withdrawal modal, idempotency replay lab, and 10-thread stress tester. *Nuance:* UI calls `/api/v1/ledger/reconcile` without passing the required `X-API-KEY: finance-admin-key`. |
| **Database Migrations** | `src/main/resources/db/migration/V1__init_schema.sql` | **Fully Implemented** | Versioned Flyway migration creating `accounts`, `payments`, `ledger_entries`, `outbox_events`, `idempotency_records`, foreign keys, indexes, and initial system reserve seed data. |
| **Integration Test Suite** | `src/test/java/com/aman/payment/test/*` (7 test classes) | **Fully Implemented (25/25 Passing)** | True end-to-end integration tests backed by Docker Testcontainers (PostgreSQL 15, Redis 7) and Spring Embedded Kafka. Verifies actual concurrency, row locks, SETNX, and Kafka delivery. |
| **Load Testing Infrastructure** | `load-tests/k6-payment-transfer.js`<br>`.github/workflows/concurrency-and-load-tests.yml` | **Fully Implemented** | Grafana k6 script executing 50 concurrent virtual users under ramping load with strict p95 < 100ms and p99 < 250ms latency thresholds. |
| **Docker & Containerization** | `Dockerfile`<br>`docker-compose.yml` | **Fully Implemented** | Multi-stage Docker build producing an Alpine JRE 17 container running as non-root `appuser`. Compose defines Postgres (5433), Redis (6380), Kafka KRaft (9092), Kafka-UI (8085), and App (8081). |
| **AWS Cloud Infrastructure** | N/A | **Missing / Blueprint Only** | Repository contains local Docker Compose and GitHub Actions CI, but no Terraform, CloudFormation, or AWS CDK scripts. (Phase 10 provides the complete production AWS architecture blueprint). |

---

## 3. Technology Stack & Technical Justifications

In a FAANG or FinTech interview, **never list a technology without explaining WHY it was chosen over its alternatives**.

| Technology | Role in Project | Technical Justification (Interview Answer) | Rejected Alternatives & Why |
|---|---|---|---|
| **Java 17 (LTS)** | Core Programming Language | Provides strong type safety, high-performance JIT compilation (C2), `record` types for immutable DTOs, enhanced pattern matching for `switch`, and strict memory management via G1GC. | **Python/Node.js:** Lack strict memory layout and true multithreaded CPU concurrency due to GIL/event-loop; unsafe for high-contention row-lock transactions. |
| **Spring Boot 3.2.5** | Microservice Framework | Enterprise-standard dependency injection, native integration with Spring Data JPA, Spring Kafka, Spring Security 6, and Micrometer Observability. Rapid auto-configuration with zero XML boilerplate. | **Micronaut / Quarkus:** Great for cold-start serverless, but Spring Boot 3 has the most mature transaction abstraction (`@Transactional`) and widest enterprise adoption. |
| **PostgreSQL 15** | Relational Database & Source of Truth | ACID compliance, robust row-level pessimistic locking (`FOR UPDATE`), lock-free queue polling (`SKIP LOCKED`), and high-precision `NUMERIC(19,4)` support for financial math. | **MongoDB / DynamoDB:** Eventual consistency and lack of native multi-document relational foreign keys make them high-risk for double-entry financial balance sheets. |
| **Redis 7** | Distributed L1 Cache & Lock | In-memory atomic commands (`SETNX` with TTL) execute in `< 1ms`, absorbing high-frequency duplicate payment clicks before they reach PostgreSQL, protecting database connection pools. | **Memcached:** Lacks atomic `SET key val NX EX` primitives and rich data structures. |
| **Apache Kafka 3.7 (KRaft)** | Distributed Event Streaming | High-throughput distributed append-only log. KRaft mode removes Zookeeper dependency. Enables decoupled, durable event publication for downstream notification, analytics, and fraud systems. | **RabbitMQ:** Excellent for complex AMQP routing, but lacks Kafka's permanent replayable commit log which is essential for financial audit event sourcing. |
| **Flyway Core** | Database Schema Migration | Version-controlled, deterministic database migrations. Prevents schema drift across development, CI/CD, and production environments. | **Hibernate `ddl-auto=update`:** Catastrophic in production; can silently drop columns, rename constraints, or lock tables during startup. |
| **Testcontainers 1.20** | Integration Testing Harness | Spins up real, disposable PostgreSQL and Redis Docker containers during tests. Eliminates "works on my machine" syndrome and tests actual row-locking and network timeouts. | **H2 In-Memory DB:** Does not support PostgreSQL's `FOR UPDATE SKIP LOCKED` or true locking semantics, creating false positives in concurrency tests. |
| **Springdoc OpenAPI 3.0** | API Documentation & Contract | Automatically inspects Spring MVC controllers and annotations to generate interactive Swagger UI (`/swagger-ui.html`) and machine-readable OpenAPI specifications (`/v3/api-docs`). | **Static Postman Collections:** Prone to becoming stale as code evolves; OpenAPI dynamically updates with the codebase. |

---

## 4. Key Architectural Trade-Offs

Every senior engineering interview revolves around **trade-offs**. Be prepared to discuss these three core decisions:

### 1. Pessimistic Locking vs. Optimistic Locking
- **What we chose:** Pessimistic row-level locking (`SELECT ... FOR UPDATE`) on accounts.
- **Trade-off:** Slightly reduced throughput under extreme contention on the same account (threads queue up), but **zero transaction rollbacks** and absolute prevention of negative balances.
- **Why not Optimistic Locking (`@Version`):** Under high concurrency (e.g., flash sale or corporate payroll account with 100 simultaneous debits), optimistic locking causes 90%+ of transactions to throw `OptimisticLockingFailureException` and roll back, forcing massive client-side retries.

### 2. Transactional Outbox (Polling) vs. Change Data Capture (CDC / Debezium)
- **What we chose:** Transactional Outbox with scheduled SQL polling using `FOR UPDATE SKIP LOCKED`.
- **Trade-off:** Introduces a small polling delay (500ms) and light database read load, but requires **zero extra infrastructure** (no Kafka Connect clusters or Debezium daemons to monitor).
- **Production Path:** In Phase 9, we document how to transition from polling to Debezium reading Postgres Write-Ahead Logs (WAL) as throughput scales beyond 5,000 TPS.

### 3. Denormalized Account Balance vs. Pure Event Sourced Balance
- **What we chose:** Dual approach: `Account.balance` stores a denormalized balance for `O(1)` read speed, while `ledger_entries` stores the immutable, append-only history.
- **Trade-off:** Requires continuous reconciliation to detect potential drift, but allows immediate balance checks without running expensive `SUM(amount)` aggregations over millions of rows on every user login.
