# 15. Project Defense Cheat Sheet: Fast-Recall Interview Guide

> **Purpose:** Keep this open right before your technical interview. Contains high-impact pitches, key design justifications, and instant answers to difficult interviewer questions.

---

## 1. The 2-Minute Project Pitch (Elevator Overview)

> *"I engineered a high-throughput, double-entry payment ledger microservice in Java 17 and Spring Boot 3.2.5 that solves the core consistency and concurrency challenges of financial systems: race conditions, double-spending, and dual-write failures.*  
>  
> *Unlike naive banking apps that simply update a balance column, this service implements strict double-entry bookkeeping. Every transfer creates immutable, balanced debit and credit entries; account balances are denormalized caches backed by an append-only ledger.*  
>  
> *To guarantee zero lost updates and prevent deadlocks under heavy concurrent load, the service acquires pessimistic row-level locks on accounts in deterministic lexicographical UUID order. It features a two-tier idempotency engine combining sub-millisecond Redis distributed locks with durable PostgreSQL payload-hash verification to prevent duplicate charges on network retries.*  
>  
> *To eliminate dual-write vulnerabilities between the database and Apache Kafka, I implemented the Transactional Outbox Pattern using PostgreSQL's `FOR UPDATE SKIP LOCKED` primitive. The entire system is covered by an automated test suite using Docker Testcontainers verifying concurrency against 20 parallel threads."*

---

## 2. The 5-Minute Technical Deep-Dive Pitch

Use this when an interviewer says: *"Tell me about the architecture and technical challenges of your ledger project."*

1. **The Domain & Hexagonal Architecture:**  
   *"I structured the project using Hexagonal Architecture (Ports and Adapters) with a rich Domain Model. The domain core contains pure Java entities with zero framework dependencies. All financial math is encapsulated in an immutable `Money` Value Object using `BigDecimal` with 4 decimal places and Banker's Rounding (`HALF_EVEN`) to eliminate cumulative rounding drift. Domain invariants are enforced inside entities: `Account` enforces status transitions (`ACTIVE`, `FROZEN`, `CLOSED`) and balance guards, while `DoubleEntryLedger` validates that `debit.amount == credit.amount` before returning a posting result."*

2. **Concurrency Control & Deadlock Prevention:**  
   *"When processing a transfer, the service coordinates pessimistic row locks (`SELECT ... FOR UPDATE`). If Alice pays Bob while Bob simultaneously pays Alice, standard locking causes circular wait deadlocks. I solved this by sorting account UUIDs: every transaction locks the smaller UUID first. Since all threads acquire locks in the exact same global order, circular wait is mathematically impossible. Transient lock contention is handled via exponential backoff retries."*

3. **Two-Tier Idempotency Layer:**  
   *"To prevent double-charging users during network timeouts, every payment requires an `Idempotency-Key`. We use a two-tier approach:  
   - Tier 1: Redis executes an atomic `SETNX` with a 30-second TTL to intercept in-flight duplicate requests in `< 1ms`.  
   - Tier 2: PostgreSQL stores a persistent `idempotency_records` row with the SHA-256 hash of the request body and cached response. If a client retries with the same key and payload, we return the cached response with zero database mutations. If the payload hash differs, we throw a `409 Conflict`."*

4. **Transactional Outbox & Apache Kafka:**  
   *"To publish events to Apache Kafka without dual-write inconsistencies, outbox rows are inserted inside the exact same ACID database transaction as the ledger entries. A scheduled relay polls pending outbox events using `SELECT ... FOR UPDATE SKIP LOCKED` every 500ms and sends them to Kafka. `SKIP LOCKED` ensures multiple horizontal service pods can poll the table in parallel without blocking each other."*

5. **Self-Healing Reconciliation & Observability:**  
   *"Account balance caches are verified continuously by a `ReconciliationService` that compares balances to `SUM(credits) - SUM(debits)`. This plugs into a custom Spring Boot Actuator `LedgerHealthIndicator`, turning health checks `DOWN` if any invariant drift occurs, alerting Prometheus and Grafana immediately."*

---

## 3. Tech Stack & 1-Sentence Technical Justifications

| Technology | 1-Sentence Technical Justification |
|---|---|
| **Java 17 (LTS)** | Provides strict type safety, C2 JIT optimization, immutable `records`, and low-latency G1GC memory management. |
| **Spring Boot 3.2.5** | Industry-standard enterprise framework providing declarative transactions (`@Transactional`) and native Kafka/Redis integrations. |
| **PostgreSQL 15** | Relational ACID source of truth supporting precise `NUMERIC(19,4)` and non-blocking `FOR UPDATE SKIP LOCKED`. |
| **Redis 7** | In-memory atomic `SETNX` distributed lock with sub-millisecond latency absorbing duplicate requests before hitting the DB. |
| **Apache Kafka 3.7 (KRaft)** | High-throughput distributed append-only log without Zookeeper dependency, guaranteeing partitioned event ordering. |
| **Flyway Core** | Version-controlled, deterministic schema migrations preventing environment schema drift. |
| **Testcontainers 1.20** | Runs real, disposable PostgreSQL and Redis Docker containers during test suites to verify actual concurrency and row locking. |

---

## 4. Top 5 Most Important Technical Challenges Overcome

### 1. Bidirectional Payment Deadlocks:
- **Challenge:** Simultaneous opposing payments between two accounts caused PostgreSQL deadlock exceptions (`40P01`).
- **Solution:** Enforced strict lexicographical UUID lock ordering (`sourceId.compareTo(destId) <= 0`).

### 2. The Dual-Write Problem:
- **Challenge:** If Kafka crashes after database commit, downstream microservices lose critical payment events.
- **Solution:** Implemented the Transactional Outbox Pattern; outbox events commit atomically in Postgres and poll with `SKIP LOCKED`.

### 3. Testcontainers Premature Teardown:
- **Challenge:** Base integration test class stopped the container between test classes, causing `Connection refused` on cached Spring datasource pools.
- **Solution:** Refactored `AbstractIntegrationTest` to the Singleton Container pattern (`static {}` block), keeping containers alive across all 25 tests.

### 4. Docker 29+ API Compatibility:
- **Challenge:** Docker Desktop 4.86+ deprecated Docker API `< 1.40`, causing Testcontainers to fail on startup.
- **Solution:** Configured `api.version=1.44` in `docker-java.properties` and pointed to `\\.\pipe\docker_engine`.

### 5. Floating-Point Rounding Drift:
- **Challenge:** Standard binary `double` types introduce decimal rounding errors across millions of transactions.
- **Solution:** Implemented an immutable `Money` Value Object using `BigDecimal` with 4 decimal places and Banker's Rounding (`HALF_EVEN`).

---

## 5. Key Design Decisions & Trade-Offs

1. **Pessimistic vs. Optimistic Locking:** Chose pessimistic row locks (`FOR UPDATE`) over `@Version` to guarantee zero transaction rollbacks under high concurrency.
2. **Denormalized Balance vs. Event Sourcing:** Chose denormalized `Account.balance` for $O(1)$ read performance combined with continuous reconciliation over calculating balances on-the-fly from millions of ledger rows.
3. **Outbox Polling vs. CDC Debezium:** Chose SQL polling (`SKIP LOCKED`) for operational simplicity and zero external infrastructure requirements; roadmap to CDC at 5,000+ TPS.

---

## 6. Known Limitations & Future Roadmap

- **Limitation 1 (Mock Auth):** Currently uses `MockJwtAuthenticationFilter` for local demo flexibility.  
  *Roadmap:* Replace with Spring Security OAuth2 Resource Server validating RS256 JWKS against Keycloak or Okta.
- **Limitation 2 (Single-Node Write Limit):** PostgreSQL single-node write capacity caps at ~2,000–5,000 TPS.  
  *Roadmap:* Implement account sharding by `hash(accountId)` and Debezium WAL streaming to scale to 50,000 TPS.
- **Limitation 3 (Single Currency):** Foreign currency transfers currently throw `CurrencyMismatchException`.  
  *Roadmap:* Add FX Exchange Rate Provider balancing cross-currency transfers through central FX clearing reserves.

---

## 7. Fast-Recall Answers to "Curveball" Questions

### "What if the power cuts out right in the middle of a transfer?"
> *"PostgreSQL's Write-Ahead Log (WAL) guarantees Atomicity. If power fails mid-transaction, uncommitted changes are rolled back on reboot via crash recovery. Because our outbox event, payments, and ledger entries share that exact same transaction, either all rows exist or none exist—zero partial state."*

### "Why did you use `@Transactional(propagation = Propagation.MANDATORY)` on `beginProcessing`?"
> *"To enforce an architectural contract. It ensures that the idempotency record creation can NEVER be called without an enclosing transaction. If a developer forgets `@Transactional` on the calling service, Spring immediately throws `IllegalTransactionStateException`, catching the bug at runtime."*

### "Why not use Kafka Transactions instead of an Outbox table?"
> *"Kafka transactions coordinate across multiple Kafka topics/partitions, but they cannot coordinate an atomic commit between Kafka and an external PostgreSQL database without a distributed Two-Phase Commit (2PC) coordinator. The Transactional Outbox Pattern solves this cleanly within a single relational database ACID boundary."*
