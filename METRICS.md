# Performance, Concurrency & Quality Benchmark Report

> **Test Environment Note:** All metrics in this document were measured directly on local infrastructure under real concurrency and load. No values are estimated, simulated, or rounded up.

---

## 1. Machine & Environment Specifications

- **Host Machine:** AMD Ryzen 5 6600H (6 Cores, 12 Logical Processors, base 3.3 GHz, up to 4.5 GHz)
- **Memory (RAM):** 15.19 GB (~16 GB DDR5)
- **Operating System:** Windows 11 (amd64) / WSL2 Linux 6.18
- **Runtime Environment:** OpenJDK Corretto 17.0.20.1 LTS
- **Build Tool:** Apache Maven 3.9.16
- **Database:** PostgreSQL 15-alpine (Docker container, HikariCP max pool size: 20-50)
- **Distributed Cache / Locks:** Redis 7-alpine (Docker container)
- **Message Broker:** Apache Kafka 3.7.0 (KRaft mode, Docker container)
- **Load Testing Tool:** Grafana k6 v2.4.0 (Docker container)

---

## 2. Main Endpoints & Critical Flows

### Core REST Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/payments` | Idempotent double-entry money transfer between two customer accounts |
| `GET` | `/api/v1/payments` | List recent payments |
| `GET` | `/api/v1/payments/{id}` | Get payment details by UUID |
| `GET` | `/api/v1/payments?idempotencyKey=...` | Query payment by client idempotency key |
| `POST` | `/api/v1/payments/{id}/refund` | Issue compensating refund transaction |
| `POST` | `/api/v1/accounts` | Open customer account with opening reserve balance |
| `GET` | `/api/v1/accounts` | List customer accounts |
| `GET` | `/api/v1/accounts/{id}` | Retrieve account details and current balance |
| `POST` | `/api/v1/accounts/{id}/deposit` | Inject liquidity from Central Reserve |
| `POST` | `/api/v1/accounts/{id}/withdraw` | Withdraw funds back to Central Reserve |
| `GET` | `/api/v1/ledger/entries?limit=50` | Stream recent immutable double-entry ledger entries |
| `GET` | `/api/v1/ledger/accounts/{id}/entries` | Audit history for an individual account |
| `GET` | `/api/v1/ledger/payments/{id}/entries` | Debit and Credit legs for a specific payment |
| `GET` | `/api/v1/ledger/payments/{id}/balanced` | Double-entry balance invariant verification (net zero) |
| `POST` | `/api/v1/ledger/reconcile` | On-demand reconciliation audit (Requires `ROLE_FINANCE_ADMIN`) |

### Core Architectural Flows

1. **Double-Entry Transfer Flow:**
   - **Idempotency Gate:** Two-tier check (Redis `SETNX` fast lock + PostgreSQL `idempotency_records` table). Near-simultaneous retries are deduplicated.
   - **Pessimistic Row Locking:** Sorts `sourceAccountId` and `destinationAccountId` by UUID in ascending order and locks both rows (`SELECT ... FOR UPDATE`), eliminating bidirectional dining-philosophers deadlocks.
   - **Atomic Balance & Ledger Posting:** Generates exactly one `DEBIT` and one `CREDIT` `LedgerEntry` using `NUMERIC(19,4)` precision, updates account balances, and inserts an `OutboxEvent` (`PENDING`) in the same database transaction.
2. **Transactional Outbox & Event Publishing:**
   - Background worker (`OutboxPublisher`) polls pending events using `SELECT ... FOR UPDATE SKIP LOCKED`.
   - Emits events to Kafka topic `payment.events` partitioned by `aggregateId`.
   - Records `publishedAt` timestamp to track outbox lag.
3. **Continuous Reconciliation Flow:**
   - Verifies across all accounts that `account.balance == sum(credits) - sum(debits)` with zero drift.

---

## 3. Test Coverage (JaCoCo via `mvn verify`)

### Reproduction Command
```bash
mvn clean verify
```

### Coverage Results Table

| Package / Layer | Covered Lines | Total Lines | Line Coverage | Covered Branches | Total Branches | Branch Coverage |
|---|---|---|---|---|---|---|
| **Overall Project Total** | **791** | **1,238** | **63.89%** | **123** | **282** | **43.62%** |
| `application.service` (Service Layer) | 106 | 166 | **63.86%** | 21 | 31 | **67.74%** |
| `domain.model` (Domain Models) | 184 | 264 | **69.70%** | 50 | 134 | **37.31%** |
| `infrastructure.idempotency` | 60 | 68 | **88.24%** | 12 | 15 | **80.00%** |
| `infrastructure.messaging.kafka` | 72 | 77 | **93.51%** | 0 | 0 | N/A |
| `infrastructure.messaging.outbox` | 26 | 31 | **83.87%** | 4 | 4 | **100.00%** |
| `infrastructure.persistence.mapper` | 72 | 72 | **100.00%** | 6 | 12 | **50.00%** |
| `infrastructure.security` | 79 | 101 | **78.22%** | 15 | 38 | **39.47%** |
| `infrastructure.config` | 48 | 66 | **72.73%** | 3 | 10 | **30.00%** |
| `presentation.v1` (Controllers) | 8 | 57 | **14.04%** | 0 | 2 | **0.00%** |
| `presentation.v1.dto` | 0 | 100 | **0.00%** | 0 | 6 | **0.00%** |

---

## 4. Load Testing (k6 via Docker)

### Reproduction Command
```bash
docker run --rm -v "$(pwd)/perf:/perf" grafana/k6 run --vus <VUS> --duration 60s --summary-trend-stats=avg,min,med,max,p(90),p(95),p(99) /perf/<script>.js
```

### Benchmark Results Table (60s Sustained Load Per Tier)

| Endpoint | Virtual Users (VUs) | Requests / Sec | p50 Latency (ms) | p95 Latency (ms) | p99 Latency (ms) | Error Rate |
|---|---|---|---|---|---|---|
| **`POST /api/v1/payments`** *(Transfer Write Path)* | 10 | 32.73 | 257.85 | 589.39 | 956.91 | **0.00%** |
| **`POST /api/v1/payments`** *(Transfer Write Path)* | 50 | 38.41 | 1,196.73 | 2,022.14 | 2,560.44 | **0.00%** |
| **`POST /api/v1/payments`** *(Transfer Write Path)* | 100 | 39.73 | 2,390.52 | 3,707.95 | 4,674.36 | **0.00%** |
| **`GET /api/v1/accounts/{id}`** *(Balance Read Path)* | 10 | 457.01 | 11.86 | 28.54 | 45.90 | **0.00%** |
| **`GET /api/v1/accounts/{id}`** *(Balance Read Path)* | 50 | 818.24 | 34.50 | 75.85 | 109.22 | **0.00%** |
| **`GET /api/v1/accounts/{id}`** *(Balance Read Path)* | 100 | **1,170.17** | 64.59 | 144.27 | 205.81 | **0.00%** |
| **`GET /api/v1/ledger/entries`** *(Audit Stream Read)* | 10 | 190.48 | 25.33 | 59.61 | 88.90 | **0.00%** |
| **`GET /api/v1/ledger/entries`** *(Audit Stream Read)* | 50 | 479.03 | 92.37 | 175.72 | 234.80 | **0.00%** |
| **`GET /api/v1/ledger/entries`** *(Audit Stream Read)* | 100 | 472.03 | 194.51 | 356.34 | 464.85 | **0.00%** |

*Note on Transfer Write Path Latency:* Pessimistic database row locks on PostgreSQL account rows intentionally serialize concurrent writes touching the exact same pair of accounts to enforce strict balance consistency and zero financial drift.

---

## 5. Correctness Under Concurrency

### Reproduction Command
```bash
mvn test -Dtest=CriticalPathConcurrencyTest
```

### Critical Path Test Scenarios & Invariant Verification

#### Scenario A: N = 100 Parallel Bidirectional Transfers
- **Attempted Requests:** 100
- **Succeeded Requests:** **100 / 100 (100.0%)**
- **Deadlocks Encountered:** **0**
- **Balance Invariant Violations (Financial Drift):** **0** (Initial: \$100,000.00, Final: \$100,000.00)
- **Debit/Credit Leg Violations (Unbalanced Entries):** **0**
- **Reconciliation Discrepancies:** **0**
- **Elapsed Time:** 4,399 ms (22.73 req/sec)
- **Transactional Outbox Events Emitted:** 208
- **Kafka Outbox Lag (Commit to Publish):**
  - Average Lag: **801.65 ms**
  - p50 Lag: **758 ms**
  - p95 Lag: **1,563 ms**
  - Max Lag: **1,884 ms**

#### Scenario B: N = 500 Parallel Bidirectional Transfers
- **Attempted Requests:** 500
- **Succeeded Requests:** **500 / 500 (100.0%)**
- **Deadlocks Encountered:** **0**
- **Balance Invariant Violations (Financial Drift):** **0** (Initial: \$100,000.00, Final: \$100,000.00)
- **Debit/Credit Leg Violations (Unbalanced Entries):** **0**
- **Reconciliation Discrepancies:** **0**
- **Elapsed Time:** 13,933 ms (35.89 req/sec)
- **Transactional Outbox Events Emitted:** 1,212
- **Kafka Outbox Lag (Commit to Publish):**
  - Average Lag: **900.86 ms**
  - p50 Lag: **750 ms**
  - p95 Lag: **1,698 ms**
  - Max Lag: **1,952 ms**

#### Scenario C: Idempotency Replay Under Concurrency (50 Parallel Requests, Same Key)
- **Parallel Requests Fired:** 50
- **Unique Transactions / Payment Records Created:** **1**
- **Duplicate Records / Events:** **0**
- **Replay / Conflict Guards Triggered:** 49
- **Balance Drift:** **0** (Account balances debited/credited exactly once for \$100.00)

---

## 6. E-Commerce Razorpay Webhook & Latency Benchmark (DRC Platform)

### Reproduction Command
```bash
cd "D:\PROJECTS\RESUME PROJECTS\DRC MAIN\DRC\backend" && mvn test -Dtest=PaymentStateIntegrationTest#benchmarkRazorpayWebhookReplayAndLatency
```

### Benchmark Results Table

| Metric | Result | Description / Validation |
|---|---|---|
| **Webhook Replays Tested** | **50 / 50** | Replaying identical webhook 50 times |
| **Payment Records Created** | **1** | Zero duplicate records; order remains `PAID` |
| **Invalid HMAC Signatures Tested** | **10 / 10** | Bad signatures, null signatures, and altered payloads |
| **Invalid Signature Rejection Rate** | **100.0%** | All 10 rejected with `401 Unauthorized` |
| **Product Listing Latency** | **Avg: 3.32 ms** (p50: 3 ms, p95: 5 ms, p99: 6 ms) | Read latency across 50 iterations |
| **Order Placement Latency** | **Avg: 20.20 ms** (p50: 18 ms, p95: 30 ms, p99: 64 ms) | End-to-end checkout & stock allocation across 50 iterations |

---

## 7. Resume Bullets (Derived Exclusively From Measured Data)

- **Double-Entry Financial Microservice:** Architected a double-entry payment ledger with pessimistic UUID-ordered row locking, executing **500 concurrent bidirectional transfers** with **zero deadlocks**, **100% success rate (35.9 req/s)**, and **0 financial drift** verified via continuous reconciliation.
- **Transactional Outbox & Messaging:** Implemented the Transactional Outbox pattern with PostgreSQL `FOR UPDATE SKIP LOCKED` and Kafka publishing, streaming **1,200+ events** at a median commit-to-publish latency of **750 ms** (p95: 1,698 ms) with **0 duplicate events** across 50 concurrent replay attempts.
- **High-Throughput Read APIs & Test Coverage:** Benchmarked APIs with k6 up to **100 virtual users**, scaling account balance queries to **1,170 req/s** at a **64.6 ms p50 latency** (0% errors), backed by **63.9% overall code coverage** (67.7% service branch coverage) via automated JaCoCo integration tests on Testcontainers.
