# 05. Database Deep Dive: PostgreSQL 15 Schema, Concurrency & SQL

> **Scope:** Forensic schema analysis, index optimization, query execution plans, locking mechanics, ACID guarantees, and 10 realistic SQL interview questions based on the actual payment ledger schema.

---

## 1. Schema Architecture & Table-by-Table Breakdown

The database is managed via Flyway (`V1__init_schema.sql`). All financial math uses PostgreSQL's fixed-precision `NUMERIC(19,4)` data type to prevent floating-point inaccuracy.

### 1. `accounts` Table
Stores customer accounts and system reserve liquidity pools.

```sql
CREATE TABLE IF NOT EXISTS accounts (
    id                  UUID PRIMARY KEY,
    account_number      VARCHAR(64) NOT NULL UNIQUE,
    owner_name          VARCHAR(255) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    balance             NUMERIC(19,4) NOT NULL DEFAULT 0,
    version             BIGINT NOT NULL DEFAULT 0,
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    is_system_account   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

| Column | Type | Constraints | Forensic Analysis & Justification |
|---|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` | Globally unique identifier; prevents enumeration attacks compared to auto-incrementing sequential integers. |
| `account_number` | `VARCHAR(64)` | `NOT NULL UNIQUE` | Human-readable account identifier (e.g. `ACC-1001`). Indexed automatically by PostgreSQL via the unique B-Tree index. |
| `owner_name` | `VARCHAR(255)` | `NOT NULL` | Legal owner or business entity name. |
| `currency` | `VARCHAR(3)` | `NOT NULL` | ISO-4217 standard currency code (USD, EUR, GBP). |
| `balance` | `NUMERIC(19,4)` | `NOT NULL DEFAULT 0` | Materialized denormalized balance cache. Enables `O(1)` balance lookups without aggregating millions of ledger entries. |
| `version` | `BIGINT` | `NOT NULL DEFAULT 0` | Optimistic locking counter mapped to `@Version` in JPA. |
| `status` | `VARCHAR(16)` | `NOT NULL DEFAULT 'ACTIVE'` | Account state: `ACTIVE`, `FROZEN`, `CLOSED`. |
| `is_system_account`| `BOOLEAN` | `NOT NULL DEFAULT FALSE`| `TRUE` for central clearing reserves (`SYSTEM_RESERVE_USD`). System accounts are exempt from negative balance checks. |
| `created_at` / `updated_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | UTC timestamp with timezone offset. Essential for historical auditing. |

**Indexes on `accounts`:**
- `accounts_pkey` (B-Tree on `id`)
- `accounts_account_number_key` (Unique B-Tree on `account_number`)
- `idx_accounts_status` (B-Tree on `status`)
- `idx_accounts_system` (B-Tree on `is_system_account`)

---

### 2. `payments` Table
Represents business-level payment intents and transfer authorizations.

```sql
CREATE TABLE IF NOT EXISTS payments (
    id                  UUID PRIMARY KEY,
    idempotency_key     VARCHAR(128) NOT NULL UNIQUE,
    source_account_id   UUID NOT NULL REFERENCES accounts(id),
    destination_account_id UUID NOT NULL REFERENCES accounts(id),
    amount              NUMERIC(19,4) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    failure_reason      VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

| Column | Type | Constraints | Forensic Analysis & Justification |
|---|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` | Payment unique transaction identifier. |
| `idempotency_key` | `VARCHAR(128)` | `NOT NULL UNIQUE` | Client-provided key ensuring that retried HTTP requests cannot create multiple payment records. |
| `source_account_id` | `UUID` | `NOT NULL REFERENCES accounts(id)` | Foreign key referencing the debited account. Indexed via `idx_payments_source_account`. |
| `destination_account_id`| `UUID` | `NOT NULL REFERENCES accounts(id)` | Foreign key referencing the credited account. Indexed via `idx_payments_dest_account`. |
| `amount` | `NUMERIC(19,4)` | `NOT NULL` | Payment transfer magnitude. Must be positive. |
| `currency` | `VARCHAR(3)` | `NOT NULL` | Currency of the transfer. |
| `status` | `VARCHAR(32)` | `NOT NULL` | `PENDING`, `COMPLETED`, `FAILED`, `REFUNDED`. |
| `failure_reason` | `VARCHAR(255)` | `NULLABLE` | Human-readable explanation if status is `FAILED` (e.g. `Insufficient balance`). |

---

### 3. `ledger_entries` Table
**The single most important table in the entire system.** This is the append-only, immutable double-entry ledger.

```sql
CREATE TABLE IF NOT EXISTS ledger_entries (
    id              UUID PRIMARY KEY,
    payment_id      UUID NOT NULL REFERENCES payments(id),
    account_id      UUID NOT NULL REFERENCES accounts(id),
    entry_type      VARCHAR(16) NOT NULL,   -- DEBIT / CREDIT
    amount          NUMERIC(19,4) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    balance_after   NUMERIC(19,4) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

| Column | Type | Constraints | Forensic Analysis & Justification |
|---|---|---|---|
| `id` | `UUID` | `PRIMARY KEY` | Unique ledger entry identifier. |
| `payment_id` | `UUID` | `NOT NULL REFERENCES payments(id)` | Links this ledger leg to its parent payment. Every payment has at least two rows here. |
| `account_id` | `UUID` | `NOT NULL REFERENCES accounts(id)` | The account affected by this debit or credit. |
| `entry_type` | `VARCHAR(16)` | `NOT NULL` | Discriminator: `DEBIT` (decreases asset) or `CREDIT` (increases liability/asset). |
| `amount` | `NUMERIC(19,4)` | `NOT NULL` | Absolute magnitude of this ledger movement. |
| `currency` | `VARCHAR(3)` | `NOT NULL` | Currency code. |
| `balance_after`| `NUMERIC(19,4)` | `NOT NULL` | Snapshot of the account's balance immediately after this entry posted. |
| `created_at` | `TIMESTAMPTZ` | `NOT NULL DEFAULT now()` | Immutable audit timestamp. **Rows in this table are NEVER updated or deleted.** |

**Indexes on `ledger_entries`:**
- `idx_ledger_account` (B-Tree on `account_id`) -> Fast account statement queries.
- `idx_ledger_payment` (B-Tree on `payment_id`) -> Fast debit/credit balance verification per transfer.
- `idx_ledger_created_at` (B-Tree on `created_at DESC`) -> High-performance streaming of recent transactions.

---

### 4. `outbox_events` Table
Implements the Transactional Outbox Pattern to eliminate dual-write inconsistencies between PostgreSQL and Kafka.

```sql
CREATE TABLE IF NOT EXISTS outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);
```
- **Composite Index:** `CREATE INDEX idx_outbox_status ON outbox_events(status, created_at);`  
  *Why:* Enables the `OutboxPublisher` to quickly fetch rows where `status = 'PENDING'` ordered by `created_at` without a full table scan.

---

### 5. `idempotency_records` Table
Provides persistent storage for request payloads and cached responses.

```sql
CREATE TABLE IF NOT EXISTS idempotency_records (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_hash    VARCHAR(128) NOT NULL,
    response_body   TEXT,
    status          VARCHAR(16) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);
```

---

## 2. Advanced Concurrency & Database Locking Deep Dive

### A. Pessimistic Locking (`SELECT ... FOR UPDATE`)
In `SpringDataAccountRepository.java`:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM AccountJpaEntity a WHERE a.id = :id")
Optional<AccountJpaEntity> findByIdForUpdate(@Param("id") UUID id);
```
- **Generated SQL:**
  ```sql
  SELECT id, account_number, balance, version, status, ... 
  FROM accounts 
  WHERE id = $1 
  FOR UPDATE;
  ```
- **PostgreSQL Mechanics:**
  - PostgreSQL places an exclusive row-level lock (`AccessExclusive` / `RowExclusiveLock`) on the tuple in the table page.
  - Any other concurrent transaction attempting to read the same row with `FOR UPDATE` or attempting to `UPDATE` / `DELETE` that row is **blocked** and placed in the lock wait queue until the locking transaction issues `COMMIT` or `ROLLBACK`.

### B. The `FOR UPDATE SKIP LOCKED` Primitive
In `SpringDataOutboxEventRepository.java`:
```sql
SELECT * FROM outbox_events
WHERE status = 'PENDING'
ORDER BY created_at ASC
LIMIT :batchSize
FOR UPDATE SKIP LOCKED;
```
- **PostgreSQL Mechanics:**
  - Normal `FOR UPDATE` waits for locked rows to become free.
  - `SKIP LOCKED` instructs PostgreSQL: *If a row is currently locked by another transaction, do NOT wait. Skip it immediately and return the next unlocked row matching the WHERE predicate.*
  - **Why this is critical for Scalability:** If you run 10 replicas of the payment microservice, every instance can poll the outbox table concurrently without lock contention or thread blocking. Each instance grabs an exclusive, non-overlapping batch of events.

---

## 3. Database Invariants & Mathematical Proofs

In double-entry bookkeeping, the system must satisfy two fundamental invariants:

$$\forall \text{ payment } p: \sum \text{debits}(p) - \sum \text{credits}(p) = 0$$

$$\forall \text{ account } a: \text{balance}(a) = \sum_{e \in \text{credits}(a)} \text{amount}(e) - \sum_{e \in \text{debits}(a)} \text{amount}(e)$$

### The System-Wide Liquidity Conservation Law:
Because initial customer funds are created via `DEBIT SYSTEM_RESERVE` and `CREDIT CUSTOMER_ACCOUNT`, the net sum of all balances across all accounts (including reserves) is **permanently zero**:

$$\sum_{a \in \text{ALL\_ACCOUNTS}} \text{balance}(a) = 0$$

---

## 4. Top 10 Realistic SQL Interview Questions Based on this Schema

### Q1: Write a query to detect any account where the denormalized balance does not match its ledger history.
```sql
SELECT 
    a.id AS account_id,
    a.account_number,
    a.balance AS cached_balance,
    COALESCE(SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE -le.amount END), 0) AS calculated_balance,
    a.balance - COALESCE(SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE -le.amount END), 0) AS discrepancy
FROM accounts a
LEFT JOIN ledger_entries le ON a.id = le.account_id
GROUP BY a.id, a.account_number, a.balance
HAVING a.balance <> COALESCE(SUM(CASE WHEN le.entry_type = 'CREDIT' THEN le.amount ELSE -le.amount END), 0);
```
- **Interviewer's Intention:** Tests your understanding of aggregation, `CASE WHEN`, `COALESCE` for accounts with no transactions, and `HAVING` filters.

### Q2: Write a query to verify if any payment in the database violates the double-entry invariant.
```sql
SELECT 
    payment_id,
    SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) AS total_debits,
    SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END) AS total_credits,
    COUNT(*) AS total_legs
FROM ledger_entries
GROUP BY payment_id
HAVING 
    SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) <> 
    SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END)
    OR COUNT(*) <> 2;
```

### Q3: How would you optimize the `ledger_entries` table if it grows to 500 million rows?
> **Answer:**  
> *"1. **Table Partitioning:** Partition `ledger_entries` by range on `created_at` (e.g. monthly partitions). This enables partition pruning so queries for recent statements only scan the current month's partition.  
> 2. **BRIN Index on `created_at`:** Since `created_at` is monotonically increasing, a Block Range Index (BRIN) uses 99% less disk space than a B-Tree index while providing near-instant range filtering.  
> 3. **Archival & Cold Storage:** Historical entries older than 7 years can be offloaded to AWS S3 / Parquet files using pg_partman."*

### Q4: What is the difference between `NUMERIC(19,4)` and `DOUBLE PRECISION` in PostgreSQL?
> **Answer:**  
> *"`NUMERIC` is an arbitrary-precision, base-10 exact representation stored in variable bytes. `DOUBLE PRECISION` is an IEEE 754 64-bit binary floating-point representation. In binary floating point, base-10 fractions like `0.10` cannot be represented precisely, causing cumulative penny drift. In banking, `NUMERIC` is strictly mandatory."*

### Q5: Write a query to find the top 5 highest-transacting accounts in the last 24 hours.
```sql
SELECT 
    a.account_number,
    a.owner_name,
    COUNT(le.id) AS transaction_count,
    SUM(le.amount) AS total_volume
FROM accounts a
JOIN ledger_entries le ON a.id = le.account_id
WHERE le.created_at >= now() - INTERVAL '24 hours'
GROUP BY a.id, a.account_number, a.owner_name
ORDER BY total_volume DESC
LIMIT 5;
```

### Q6: What happens if two transactions execute `SELECT ... FOR UPDATE` on the same row simultaneously?
> **Answer:**  
> *"The first transaction acquires the tuple lock. The second transaction blocks at the engine level in `heap_lock_tuple`. When the first transaction commits, the second transaction unblocks, re-reads the committed updated tuple (in `READ_COMMITTED` isolation), and acquires its lock. If the first transaction rolls back, the second transaction immediately acquires the lock on the original tuple."*

### Q7: Why do we have indexes on `source_account_id` and `destination_account_id` in `payments`?
> **Answer:**  
> *"Because PostgreSQL does NOT automatically index foreign key columns! It only indexes primary keys and unique constraints automatically. Without explicit indexes on foreign keys, running a query like `SELECT * FROM payments WHERE source_account_id = ?` or deleting an account results in a full table scan on `payments`."*

### Q8: How does PostgreSQL handle MVCC (Multi-Version Concurrency Control) during an `UPDATE`?
> **Answer:**  
> *"In PostgreSQL, an `UPDATE` does not overwrite the row on disk. It marks the old row version's `xmax` header as committed and inserts a brand new tuple with its `xmin` set to the current transaction ID. Old row versions remain on disk as dead tuples until reclaimed by `VACUUM`."*

### Q9: Write a query to find pending outbox events that have failed more than 3 times.
```sql
SELECT id, aggregate_id, event_type, retry_count, created_at
FROM outbox_events
WHERE status = 'FAILED' AND retry_count >= 3
ORDER BY retry_count DESC;
```

### Q10: What is an index-only scan, and how does `idx_ledger_account` support it?
> **Answer:**  
> *"An index-only scan retrieves the query result directly from the B-Tree index leaf pages without fetching the actual table heap pages (providing 5x-10x speedup). In our index `CREATE INDEX idx_ledger_account ON ledger_entries(account_id)`, queries like `SELECT COUNT(*) FROM ledger_entries WHERE account_id = ?` can be satisfied purely from the index."*
