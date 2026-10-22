# payment-ledger

A production-grade, high-throughput double-entry payment ledger microservice (Spring Boot 3 / Java 17) featuring strict double-entry bookkeeping, two-tier idempotency, the transactional outbox pattern, deadlock-safe row locking, periodic reconciliation, and a modern real-time interactive dashboard.

---

## Architecture Overview

```
Client / Dashboard -> PaymentController -> PaymentService -> LedgerService -> DoubleEntryLedger
                             |                                                       |
                      IdempotencyService                                Debit / Credit Processors
                             |                                                       |
                   IdempotencyKeyService                                   AccountRepository
                     (Redis + Postgres)                                    (Pessimistic Row Locks)

PaymentService writes OutboxEvent in the same DB transaction as Payment/LedgerEntry rows.
OutboxPublisher polls PENDING rows (FOR UPDATE SKIP LOCKED) and publishes to Kafka.
```

### Key Design Decisions

| Concern | Approach |
|---|---|
| **Double-entry integrity** | Every financial movement posts exactly one `DEBIT` and one `CREDIT` `LedgerEntry` inside one atomic DB transaction. `LedgerEntry` rows are append-only; `Account.balance` is a denormalized cache reconciled by `ReconciliationService`. Deposits & withdrawals are balanced against a dedicated `SYSTEM_RESERVE` clearing account. |
| **Two-Tier Idempotency** | Caller-supplied `idempotencyKey`. Redis `SETNX` acts as an instantaneous distributed lock against near-simultaneous retries. The `idempotency_records` Postgres table provides durable ground truth. Reusing a key with identical payload replays the cached response; key collision with mismatched payload throws `DuplicatePaymentException`. |
| **Concurrency / Deadlock Safety** | Row-level locks (`SELECT ... FOR UPDATE`). `DoubleEntryLedger` acquires account locks in a fixed order (smaller UUID first) regardless of debit/credit role, eliminating bidirectional "dining philosophers" deadlocks. Contention timeouts are retried with exponential backoff. |
| **Transactional Outbox & Kafka** | Outbox rows commit atomically with the payment. `OutboxPublisher` polls pending events with `FOR UPDATE SKIP LOCKED`, supporting scaled-out publisher nodes with exponential backoff and DLQ handling. |
| **Continuous Reconciliation** | `ReconciliationService` runs hourly and on-demand (`POST /api/v1/ledger/reconcile`) to verify that all account balances equal the sum of their ledger entries and all payments net to zero. Never auto-mutates financial records silently. |

---

## Live Interactive Dashboard & Documentation

When running locally, open your browser to access:
- **Interactive Web Dashboard**: [http://localhost:8080](http://localhost:8080)
  - Account Manager & Liquidity Top-up / Withdrawal
  - Idempotency Replay Lab & 10-Thread Concurrency Stress Tester
  - Live Double-Entry Audit Stream
  - On-Demand Invariant Reconciliation Auditor
- **OpenAPI 3 / Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Spring Boot Actuator Health**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **Prometheus Metrics Endpoint**: [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus)
- **Kafka-UI Console**: [http://localhost:8085](http://localhost:8085)

---

## Running Locally

### 1. One-Click Infrastructure with Docker Compose
Starts PostgreSQL, Redis, Kafka (KRaft), and Kafka UI:

```bash
docker compose up -d postgres redis kafka kafka-ui
```

### 2. Run the Spring Boot Application
Database migrations run automatically on startup via Flyway:

```bash
mvn spring-boot:run
```

*(Alternatively, build and run containerized with `docker compose --profile app up -d`)*

---

## REST API Reference

### Accounts
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/accounts` | Open customer account (initial balance funded via Central Reserve) |
| `GET` | `/api/v1/accounts` | List all customer accounts |
| `GET` | `/api/v1/accounts/{id}` | Get account by ID |
| `GET` | `/api/v1/accounts?accountNumber=...` | Get account by account number |
| `POST` | `/api/v1/accounts/{id}/deposit` | Deposit liquidity from Central Reserve |
| `POST` | `/api/v1/accounts/{id}/withdraw` | Withdraw funds to Central Reserve |
| `PATCH` | `/api/v1/accounts/{id}/status` | Update account status (`ACTIVE`, `FROZEN`, `CLOSED`) |

### Payments & Transfers
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/payments` | Initiate double-entry transfer (`idempotencyKey` required) |
| `GET` | `/api/v1/payments` | List recent payments |
| `GET` | `/api/v1/payments/{id}` | Get payment by ID |
| `GET` | `/api/v1/payments?idempotencyKey=...` | Get payment by client idempotency key |
| `POST` | `/api/v1/payments/{id}/refund` | Refund completed payment (reverses double-entry legs) |

### Ledger & Reconciliation
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/ledger/entries?limit=50` | Stream recent immutable ledger entries |
| `GET` | `/api/v1/ledger/accounts/{id}/entries` | Audit history for an account |
| `GET` | `/api/v1/ledger/payments/{id}/entries` | Debit and Credit legs for a payment |
| `GET` | `/api/v1/ledger/payments/{id}/balanced` | Check whether a payment nets to zero |
| `POST` | `/api/v1/ledger/reconcile` | Trigger instant reconciliation pass |

---

## Integration Tests

Tests run against real Testcontainers (Postgres, Redis, and Embedded Kafka) to verify real transactional and lock semantics:

```bash
mvn test
```
