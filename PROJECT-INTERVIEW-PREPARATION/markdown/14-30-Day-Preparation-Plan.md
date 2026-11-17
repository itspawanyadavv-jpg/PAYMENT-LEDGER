# 14. 30-Day SDE-1 / Java Backend Interview Preparation Masterplan

> **Target Role:** SDE-1 / Java Backend Engineer  
> **Timeline:** 30 Days (4 Weeks)  
> **Daily Allocation:** 4–5 Hours / Day (1.5h Project Deep Dive & Defense + 1h Java/Spring Theory + 1h SQL/DB + 1.5h DSA)

---

## Week 1: Core Domain, Concurrency & Java 17 Mastery

### Day 1: Project Architecture & Hexagonal Structure
- **Project Goal:** Memorize and sketch the Hexagonal Architecture diagram on paper without looking.
- **Java Concept:** Java 17 features: Records, sealed classes, pattern matching switch, immutability.
- **Deliverable:** Explain the difference between Inbound Ports and Outbound Ports out loud in 3 minutes.
- **DSA:** 2 LeetCode Mediums on Two Pointers (e.g., 3Sum, Container With Most Water).

### Day 2: The Double-Entry Invariant Engine
- **Project Goal:** Trace `DoubleEntryLedger.java` and `Money.java`. Understand `BigDecimal` Banker's Rounding (`HALF_EVEN`).
- **Java Concept:** IEEE 754 floating-point limitations, `BigDecimal` memory layout, value objects.
- **Deliverable:** Complete Hands-On Assignment 7 (Predicting output & finding bugs).
- **DSA:** 2 LeetCode Mediums on Sliding Window (e.g., Longest Substring Without Repeating Characters).

### Day 3: Deadlock Prevention & Row Locking
- **Project Goal:** Walk through `firstId.compareTo(secondId) <= 0` in `PaymentApplicationService.java`.
- **Java Concept:** Operating system deadlocks: Coffman's 4 conditions (Mutual exclusion, Hold & wait, No preemption, Circular wait).
- **Deliverable:** Explain how sorting UUIDs breaks circular wait to an imaginary interviewer.
- **DSA:** 2 LeetCode Mediums on HashMaps / Prefix Sum (e.g., Subarray Sum Equals K).

### Day 4: Spring Transactional Mechanics & Proxies
- **Project Goal:** Understand `@Lazy PaymentApplicationService self` and Spring CGLIB dynamic proxies.
- **Spring Concept:** `@Transactional` rollback rules, transaction propagation (`REQUIRED`, `REQUIRES_NEW`, `MANDATORY`).
- **Deliverable:** Write a test verifying that `Propagation.REQUIRES_NEW` commits even if the parent rolls back.
- **DSA:** 2 LeetCode Mediums on Stack (e.g., Daily Temperatures, Min Stack).

### Day 5: Spring Boot Lifecycle & IoC Internals
- **Project Goal:** Trace `PaymentApplication.java` from `main()` to embedded Tomcat startup.
- **Spring Concept:** Bean lifecycles, `@PostConstruct`, `@Autowired`, Constructor vs Field injection.
- **Deliverable:** Explain why constructor injection is preferred over field injection in 3 bullet points.
- **DSA:** 2 LeetCode Mediums on Binary Search (e.g., Search in Rotated Sorted Array).

### Day 6: Unit Testing with Mockito
- **Project Goal:** Complete Hands-On Assignment 8 (Mocking outbound ports with Mockito).
- **Testing Concept:** `@Mock`, `@InjectMocks`, `when().thenReturn()`, `verify()`.
- **Deliverable:** Push a passing Mockito unit test to a local branch.
- **DSA:** 2 LeetCode Mediums on Linked Lists (e.g., Reverse Linked List II, Reorder List).

### Day 7: Week 1 Checkpoint & Self-Mock Interview
- **Mock Interview Drill:** Record yourself answering Questions 1–25 from `12-Project-Interview-Questions.md`.
- **Revision:** Re-read `01-Project-Overview.md` and `04-Backend-Deep-Dive.md`.

---

## Week 2: Database, SQL & Two-Tier Idempotency

### Day 8: PostgreSQL 15 Relational Schema
- **Project Goal:** Inspect `V1__init_schema.sql`. Master table definitions and foreign key constraints.
- **Database Concept:** B-Tree index anatomy, composite indexes, primary key clustering.
- **Deliverable:** Draw the full ERD from memory.
- **DSA:** 2 LeetCode Mediums on Trees (e.g., Lowest Common Ancestor, Validate BST).

### Day 9: Pessimistic Locking (`SELECT ... FOR UPDATE`)
- **Project Goal:** Analyze `SpringDataAccountRepository.findByIdForUpdate()`.
- **SQL Concept:** PostgreSQL lock modes: `RowShare`, `RowExclusive`, `AccessExclusive`.
- **Deliverable:** Run `EXPLAIN ANALYZE` on a locked query in Docker Postgres.
- **DSA:** 2 LeetCode Mediums on Binary Tree Traversals (e.g., Level Order Traversal, Zigzag).

### Day 10: PostgreSQL `FOR UPDATE SKIP LOCKED`
- **Project Goal:** Walk through `SpringDataOutboxEventRepository.lockNextBatch()`.
- **Database Concept:** Queue-like patterns in RDBMS, MVCC dead tuple vacuuming.
- **Deliverable:** Solve SQL Interview Questions 1–4 from `05-Database-Deep-Dive.md`.
- **DSA:** 2 LeetCode Mediums on Graphs / BFS (e.g., Number of Islands, Rotting Oranges).

### Day 11: Redis Distributed Locking (`SETNX`)
- **Project Goal:** Inspect `IdempotencyKeyService.java` Redis interaction.
- **Redis Concept:** Single-threaded event loop, atomic primitives, memory eviction policies.
- **Deliverable:** Explain why Redis `SETNX` needs a TTL to prevent permanent deadlocks on pod crash.
- **DSA:** 2 LeetCode Mediums on Graphs / DFS (e.g., Clone Graph, Course Schedule).

### Day 12: Idempotency Payload Hashing & Tampering Prevention
- **Project Goal:** Trace SHA-256 payload hashing in `IdempotencyKeyService.java`.
- **System Design:** Stripe-style idempotency headers, payload verification, response caching.
- **Deliverable:** Test replaying a duplicate request in the web dashboard at `localhost:8081`.
- **DSA:** 2 LeetCode Mediums on Dynamic Programming (e.g., Climbing Stairs, Coin Change).

### Day 13: SQL Mastery & Invariant Aggregations
- **Project Goal:** Solve SQL Interview Questions 5–10 from `05-Database-Deep-Dive.md`.
- **SQL Concept:** Window functions (`ROW_NUMBER()`, `RANK()`), Common Table Expressions (CTEs), `HAVING`.
- **Deliverable:** Write a query that computes the running balance of an account over time.
- **DSA:** 2 LeetCode Mediums on DP (e.g., Longest Increasing Subsequence).

### Day 14: Week 2 Checkpoint & SQL Drill
- **Mock Interview Drill:** Answer Questions 26–52 from `12-Project-Interview-Questions.md`.
- **Deliverable:** Complete Hands-On Assignment 4 (Paginated Historical Statement Query).

---

## Week 3: Distributed Messaging, Kafka & Security

### Day 15: The Dual-Write Problem & Transactional Outbox
- **Project Goal:** Study `OutboxPublisher.java` and `OutboxService.java`.
- **Distributed Concept:** Dual-write inconsistencies, two-phase commit vs eventual consistency.
- **Deliverable:** Sketch the 3-step outbox sequence diagram on paper.
- **DSA:** 2 LeetCode Mediums on Heaps / PriorityQueue (e.g., Top K Frequent Elements).

### Day 16: Apache Kafka Architecture (KRaft Mode)
- **Project Goal:** Inspect `docker-compose.yml` Kafka configuration and `KafkaPaymentEventProducer.java`.
- **Kafka Concept:** Topics, Partitions, Consumer Groups, Offsets, Leader-Follower replication, ISR.
- **Deliverable:** Explain why `aggregateId` must be the Kafka message key for financial events.
- **DSA:** 2 LeetCode Mediums on Backtracking (e.g., Subsets, Permutations).

### Day 17: Outbox Polling vs. CDC / Debezium
- **Project Goal:** Read Phase 8 and Phase 9 notes on scaling beyond 5,000 TPS.
- **System Design:** PostgreSQL WAL (Write-Ahead Logging), Change Data Capture (CDC), Kafka Connect.
- **Deliverable:** Be able to explain the pros and cons of Polling vs Debezium in an interview.
- **DSA:** 2 LeetCode Mediums on Trie (e.g., Implement Trie, Word Search II).

### Day 18: Spring Security 6 & Stateless Architecture
- **Project Goal:** Inspect `SecurityConfig.java` and `ApiKeyAuthenticationFilter.java`.
- **Security Concept:** `SecurityFilterChain`, `AuthenticationManager`, `SecurityContextHolder`, CSRF.
- **Deliverable:** Complete Hands-On Assignment 5 (Fixing UI reconciliation header in `app.js`).
- **DSA:** 2 LeetCode Mediums on Intervals (e.g., Merge Intervals, Insert Interval).

### Day 19: JWT Authentication & OWASP Security Audit
- **Project Goal:** Study `08-Security-Audit.md` and `MockJwtAuthenticationFilter.java`.
- **Security Concept:** JWT structure (Header, Payload, Signature), RS256 vs HS256, JWKS, IDOR.
- **Deliverable:** Complete Hands-On Assignment 2 (Cryptographic JWT signature verification).
- **DSA:** 2 LeetCode Mediums on Greedy (e.g., Jump Game, Gas Station).

### Day 20: Testcontainers & Concurrency Integration Testing
- **Project Goal:** Study `AbstractIntegrationTest.java` and `ConcurrentPaymentTest.java`.
- **Testing Concept:** Docker named pipes (`npipe`), dynamic ports, Testcontainers singleton pattern.
- **Deliverable:** Explain the Testcontainers bug we diagnosed and fixed in your own words.
- **DSA:** 2 LeetCode Mediums on Matrix Manipulation (e.g., Set Matrix Zeroes, Spiral Matrix).

### Day 21: Week 3 Checkpoint & System Design Drill
- **Mock Interview Drill:** Answer Questions 53–76 from `12-Project-Interview-Questions.md`.
- **System Design:** Design a Distributed Payment Processing System on a whiteboard.

---

## Week 4: Performance, High-Speed Defense & Mock Interviews

### Day 22: Connection Pool Tuning & Bottlenecks
- **Project Goal:** Study `09-Performance-Scalability.md`. Understand HikariCP sizing math.
- **Performance Concept:** Thread starvation, connection pool exhaustion, CPU vs I/O bound tuning.
- **Deliverable:** Calculate maximum theoretical TPS given pool size 20 and latency 50ms.
- **DSA:** 2 LeetCode Hard problems (e.g., Trapping Rain Water).

### Day 23: Scaling from 100 to 100,000 Users
- **Project Goal:** Memorize the exact behavior of your system at 100, 1k, 10k, and 100k users.
- **System Design:** Horizontal scaling, database sharding (`hash(accountId)`), Redis clustering.
- **Deliverable:** Defend your scaling strategy against an imaginary FAANG interviewer.
- **DSA:** 2 LeetCode Mediums on Bit Manipulation.

### Day 24: Docker Multi-Stage Builds & JVM Flags
- **Project Goal:** Review `Dockerfile` and `10-Docker-AWS-Deployment.md`.
- **DevOps Concept:** Multi-stage builds, non-root users, `-XX:+UseG1GC`, `-XX:MaxRAMPercentage=75.0`.
- **Deliverable:** Explain why Alpine + Temurin JRE was chosen over a full JDK base image.
- **DSA:** Top 10 most common Amazon/FAANG tagged problems review.

### Day 25: Observability & Continuous Reconciliation
- **Project Goal:** Inspect `LedgerHealthIndicator.java` and `/actuator/prometheus`.
- **Observability:** Invariant checking, Prometheus metrics, Grafana dashboards, RED method.
- **Deliverable:** Trigger a simulated drift and demonstrate Actuator health turning `DOWN`.
- **DSA:** Review all Tree and Graph patterns.

### Day 26: Hands-On Live Code Challenge Prep
- **Project Goal:** Practice adding a new REST endpoint from scratch in under 15 minutes.
- **Deliverable:** Complete Hands-On Assignment 1 (Daily transfer limit).
- **DSA:** Review all Sliding Window and Two Pointer patterns.

### Day 27: Full 1-Hour Live Mock Technical Interview
- **Interview Simulation:** Execute Phase 12 mock interview:
  - 5-minute project overview.
  - 20-minute deep-dive grilling on row locks, outbox, and idempotency.
  - 15-minute live coding challenge.
  - 10-minute system design follow-ups.

### Day 28: Weakness Remediation & Edge Case Review
- **Project Goal:** Review all 102 questions from `12-Project-Interview-Questions.md`.
- **Deliverable:** Identify any question you hesitated on and review its technical explanation.

### Day 29: Final Cheat-Sheet Memorization
- **Project Goal:** Memorize `15-Project-Defense-Cheat-Sheet.md`.
- **Deliverable:** Practice the 2-minute and 5-minute pitches out loud until fluent without pausing.

### Day 30: Interview Day: Calm, Confident & Authoritative
- **Mindset:** You built this. You tested it against 20 concurrent threads. You understand the bytecode, the SQL engine, and the distributed consensus. You are ready to ace the interview!
