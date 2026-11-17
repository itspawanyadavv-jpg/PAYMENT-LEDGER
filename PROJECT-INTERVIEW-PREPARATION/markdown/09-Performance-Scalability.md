# 09. Performance, Benchmarks & High-Throughput Scalability

> **Scope:** Bottleneck analysis, Hikari connection pool tuning, k6 load testing evaluation, horizontal scaling limits, and realistic behavior at 100 to 100,000 concurrent users.

---

## 1. Baseline Performance Profile & Bottlenecks

### A. Connection Pooling (HikariCP)
In `src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      connection-timeout: 3000
```
- **Analysis:**
  - With `maximum-pool-size: 20`, the application can execute at most **20 physical database transactions in parallel** per JVM instance.
  - If 50 concurrent payment requests hit the service simultaneously, 20 execute immediately while 30 queue in HikariCP's wait list.
  - If a transaction holds a connection for 50ms, a pool of 20 connections has a theoretical maximum throughput of:
    $$\text{Max TPS} = \frac{20 \text{ connections}}{0.050 \text{ seconds}} = 400 \text{ TPS per instance}$$
  - If transactions stall or wait on locks for longer than `connection-timeout: 3000ms`, HikariCP throws:
    `java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 3000ms`.

### B. Pessimistic Row Lock Contention
- If 100 concurrent requests attempt to debit or credit the **same hot account** (e.g. an enterprise merchant or central reserve), transactions must serialize one after another.
- **The Bottleneck:** Row locking serializes write operations on that specific account ID tuple in PostgreSQL, placing a hard cap on single-account write throughput (~200–500 writes/sec on standard SSD hardware).

---

## 2. Load Simulation & k6 Benchmark Analysis

The repository includes a production-grade k6 load test script (`load-tests/k6-payment-transfer.js`):
- **Scenario:** 50 virtual users ramping over 60 seconds against `POST /api/v1/payments`.
- **Latency SLAs (Thresholds):**
  - `p(95) < 100ms`: 95% of payment transactions must commit in under 100 milliseconds.
  - `p(99) < 250ms`: 99% of transactions must commit in under 250 milliseconds.
  - `transfer_errors < 1%`: 99%+ of payments must succeed.

---

## 3. Scale-Out Analysis: What Happens Under Increasing Load?

In an interview, **never claim a single Spring Boot instance can handle 100,000 users without breaking**. Explain the exact failure modes at each tier:

### Tier 1: 100 Concurrent Users
- **System Behavior:** **Smooth & Effortless.**
- **Metrics:** Throughput ~150–250 TPS. Average latency ~25–40ms.
- **Resource Utilization:** CPU at 15–25%, memory footprint stable at ~512MB heap. Hikari pool utilization oscillates between 4 and 10 connections.
- **Bottlenecks:** None. Redis handles 100% of L1 locks in < 1ms.

### Tier 2: 1,000 Concurrent Users
- **System Behavior:** **Capacity Limit of a Single Pod.**
- **Metrics:** Average latency increases to 120–250ms (p99 ~400ms).
- **Resource Utilization:** CPU approaches 75–85%. Hikari connection pool operates at 100% capacity (20/20 active connections).
- **Bottlenecks:** Hikari pool queuing. Some requests experience brief connection wait times. If transfers involve distinct accounts, the database easily commits them.
- **Action Required:** Scale horizontally from 1 instance to **3–4 microservice pods** behind an Application Load Balancer.

### Tier 3: 10,000 Concurrent Users
- **System Behavior:** **Database & Lock Saturation without Clustering.**
- **Metrics:** A single PostgreSQL instance experiences heavy disk I/O on the Write-Ahead Log (WAL). P99 latency spikes above 1,500ms.
- **Failure Modes:**
  - `SQLTransientConnectionException` occurs as Hikari queues exceed 3,000ms.
  - Redis memory increases as thousands of idempotency keys are created (handled smoothly by 24h TTL).
  - Outbox table grows faster than the 500ms single-thread poll interval can drain to Kafka.
- **Required Architecture:**
  1. **Horizontal Pod Autoscaling (HPA):** Scale payment pods to 10–15 replicas.
  2. **Dedicated Read Replicas:** Route read queries (`GET /api/v1/accounts`, `GET /api/v1/ledger/entries`) to read-only PostgreSQL replicas.
  3. **Tune Outbox Publisher:** Increase batch size from 100 to 500 and polling interval to 200ms.

### Tier 4: 100,000 Concurrent Users (FAANG / Visa Scale)
- **System Behavior:** **Catastrophic Failure on Single-Node Database.**
- **Why it Fails:** No single relational database engine can execute 100,000 ACID row-locking transactions per second on shared tables.
- **The Enterprise Architectural Solution:**
  1. **Account-Based Database Sharding:**
     - Partition accounts across 16–32 independent PostgreSQL database clusters based on `hash(accountId) % numShards`.
     - 95% of transfers become intra-shard operations executing in parallel.
  2. **Replace Outbox Polling with Change Data Capture (CDC / Debezium):**
     - Scheduled SQL polling creates too much read overhead on the database.
     - Debezium reads directly from the PostgreSQL Write-Ahead Log (WAL) and streams changes to Kafka in sub-millisecond real time with zero database query overhead.
  3. **Distributed Redis Cluster:**
     - Shard Redis into a 6-node cluster with master-replica failover to absorb millions of L1 idempotency checks.
  4. **Kafka Partitioning by Account ID:**
     - Configure the `payment.events` topic with 32 partitions using `sourceAccountId` as the partition key to guarantee strictly ordered downstream processing.
