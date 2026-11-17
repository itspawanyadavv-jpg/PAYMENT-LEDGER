# 03. System Architecture Deep Dive & Mermaid Blueprints

> **Scope:** Comprehensive architectural analysis from beginner to FAANG-staff level, covering request lifecycles, concurrency boundaries, data pipelines, and deployment topologies with 7 Mermaid diagrams.

---

## 1. High-Level System Architecture

The Payment Ledger is structured as an event-driven, hexagonal microservice backed by high-durability relational storage, distributed in-memory locking, and an asynchronous distributed commit log.

### Diagram 1: High-Level System Architecture

```mermaid
flowchart TD
    subgraph Clients["Client Layer"]
        WebDashboard["Web Dashboard (Browser HTML5/JS)"]
        ExternalAPI["External FinTech Clients / Gateways"]
    end

    subgraph Security_Perimeter["Edge Security Perimeter"]
        ReverseProxy["Reverse Proxy / Nginx / AWS ALB"]
        SecurityFilter["Spring Security 6 (Stateless Filter Chain)"]
    end

    subgraph App_Server["Payment Ledger Microservice (Spring Boot 3)"]
        Controllers["REST Controllers (/api/v1/*)"]
        AppServices["Hexagonal Application Services"]
        DomainCore["Pure Domain Core (Money, Account, DoubleEntryLedger)"]
        OutboxRelay["Outbox Publisher (@Scheduled Worker)"]
    end

    subgraph Storage_Tier["Data & Cache Tier"]
        RedisCluster[("Redis 7 (In-Flight SETNX Locks)")]
        PostgresDB[("PostgreSQL 15 (ACID Source of Truth)")]
    end

    subgraph Messaging_Tier["Event Streaming Tier"]
        KafkaBroker["Apache Kafka 3.7 (KRaft Broker)"]
        KafkaUI["Kafka-UI Console (Port 8085)"]
        EventConsumer["PaymentEventConsumer (Audit Log)"]
    end

    Clients -->|"HTTPS / REST"| ReverseProxy
    ReverseProxy --> SecurityFilter
    SecurityFilter --> Controllers
    Controllers --> AppServices
    AppServices --> DomainCore
    AppServices -->|"L1 Lock Check"| RedisCluster
    AppServices -->|"ACID Transaction"| PostgresDB
    OutboxRelay -->|"SELECT FOR UPDATE SKIP LOCKED"| PostgresDB
    OutboxRelay -->|"Publish payment.events"| KafkaBroker
    KafkaBroker --> KafkaUI
    KafkaBroker --> EventConsumer
```

### Step-by-Step Explanation:
1. **Client Interaction:** Users interact via the Single-Page Web Dashboard (`localhost:8081`) or via programmatic REST API calls supplying an `Idempotency-Key` header.
2. **Security Interception:** Requests enter Spring Security's filter chain (`SecurityConfig`). Static web assets and OpenAPI docs bypass auth; customer endpoints require Bearer tokens or `X-API-KEY`.
3. **Hexagonal Orchestration:** Controllers invoke Inbound Ports (`ProcessPaymentUseCase`). Application services orchestrate domain business logic, coordinating Redis locks and PostgreSQL database transactions.
4. **Data Durability:** Accounts and ledger entries are committed atomically in PostgreSQL inside a single ACID boundary.
5. **Decoupled Event Streaming:** Domain events written to `outbox_events` are polled asynchronously by the `OutboxPublisher` and dispatched to Kafka, ensuring zero dual-write vulnerabilities.

---

## 2. Backend Component Architecture (Hexagonal Ports & Adapters)

This service strictly adheres to **Hexagonal Architecture** (also known as Ports and Adapters) and **Domain-Driven Design (DDD)**.

### Diagram 2: Hexagonal Ports & Adapters Architecture

```mermaid
flowchart LR
    subgraph Driving_Adapters["Primary (Driving) Adapters"]
        WebUI["Web Browser"] --> AccountCtrl["AccountController"]
        WebUI --> PaymentCtrl["PaymentController"]
        WebUI --> LedgerCtrl["LedgerController"]
    end

    subgraph Inbound_Ports["Application Inbound Ports (Interfaces)"]
        AccountCtrl --> ManageAccountPort["ManageAccountUseCase"]
        PaymentCtrl --> ProcessPaymentPort["ProcessPaymentUseCase"]
        PaymentCtrl --> GetPaymentPort["GetPaymentUseCase"]
        PaymentCtrl --> RefundPaymentPort["RefundPaymentUseCase"]
        LedgerCtrl --> QueryLedgerPort["QueryLedgerUseCase"]
    end

    subgraph App_Services["Application Services (Implementations)"]
        ManageAccountPort -.-> AccountService["AccountApplicationService"]
        ProcessPaymentPort -.-> PaymentService["PaymentApplicationService"]
        GetPaymentPort -.-> PaymentService
        RefundPaymentPort -.-> PaymentService
        QueryLedgerPort -.-> LedgerService["LedgerApplicationService"]
    end

    subgraph Domain_Core["Domain Core (Pure Business Logic)"]
        AccountService --> AccountModel["Account (Entity)"]
        PaymentService --> DoubleEntry["DoubleEntryLedger (Domain Service)"]
        PaymentService --> MoneyModel["Money (Value Object)"]
        PaymentService --> PaymentModel["Payment (Entity)"]
        DoubleEntry --> LedgerEntryModel["LedgerEntry (Entity)"]
    end

    subgraph Outbound_Ports["Application Outbound Ports (Interfaces)"]
        PaymentService --> AcctRepoPort["AccountRepositoryPort"]
        PaymentService --> PayRepoPort["PaymentRepositoryPort"]
        PaymentService --> LedgRepoPort["LedgerEntryRepositoryPort"]
        PaymentService --> IdemPort["IdempotencyPort"]
        PaymentService --> EventPubPort["PaymentEventPublisherPort"]
    end

    subgraph Driven_Adapters["Secondary (Driven) Adapters"]
        AcctRepoPort -.-> AcctAdapter["AccountRepositoryAdapter"]
        PayRepoPort -.-> PayAdapter["PaymentRepositoryAdapter"]
        LedgRepoPort -.-> LedgAdapter["LedgerEntryRepositoryAdapter"]
        IdemPort -.-> IdemAdapter["IdempotencyAdapter"]
        EventPubPort -.-> OutboxSvc["OutboxService"]

        AcctAdapter --> SpringDataAcct["SpringDataAccountRepository"]
        PayAdapter --> SpringDataPay["SpringDataPaymentRepository"]
        LedgAdapter --> SpringDataLedg["SpringDataLedgerEntryRepository"]
        IdemAdapter --> IdemKeySvc["IdempotencyKeyService"]
        OutboxSvc --> SpringDataOutbox["SpringDataOutboxEventRepository"]
    end
```

### Architectural Rules Enforced:
1. **Dependency Inversion Principle:** The Domain Core has **zero dependencies** on Spring, Hibernate, Redis, or Kafka.
2. **Testability:** Domain models (`DoubleEntryLedgerTest`, `MoneyTest`) are unit tested without starting Spring, executing in milliseconds.
3. **Pluggable Persistence:** If PostgreSQL is replaced with another datastore, only the Driven Adapters (`*RepositoryAdapter`) change; the business logic remains untouched.

---

## 3. Database Entity Relationship Diagram (ERD)

The relational schema is normalized to prevent anomalies while providing an immutable financial audit trail.

### Diagram 3: Database Entity Relationship Diagram

```mermaid
erDiagram
    ACCOUNTS ||--o{ PAYMENTS : "acts as source"
    ACCOUNTS ||--o{ PAYMENTS : "acts as destination"
    ACCOUNTS ||--o{ LEDGER_ENTRIES : "owns entries"
    PAYMENTS ||--|{ LEDGER_ENTRIES : "generates debit and credit legs"
    PAYMENTS ||--o{ OUTBOX_EVENTS : "triggers domain events"
    PAYMENTS ||--o| IDEMPOTENCY_RECORDS : "linked via idempotency_key"

    ACCOUNTS {
        UUID id PK
        VARCHAR(64) account_number UK
        VARCHAR(255) owner_name
        VARCHAR(3) currency
        NUMERIC(19_4) balance
        BIGINT version
        VARCHAR(16) status
        BOOLEAN is_system_account
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    PAYMENTS {
        UUID id PK
        VARCHAR(128) idempotency_key UK
        UUID source_account_id FK
        UUID destination_account_id FK
        NUMERIC(19_4) amount
        VARCHAR(3) currency
        VARCHAR(32) status
        VARCHAR(255) failure_reason
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    LEDGER_ENTRIES {
        UUID id PK
        UUID payment_id FK
        UUID account_id FK
        VARCHAR(16) entry_type "DEBIT or CREDIT"
        NUMERIC(19_4) amount
        VARCHAR(3) currency
        NUMERIC(19_4) balance_after
        TIMESTAMPTZ created_at
    }

    OUTBOX_EVENTS {
        UUID id PK
        VARCHAR(64) aggregate_type
        UUID aggregate_id
        VARCHAR(64) event_type
        TEXT payload
        VARCHAR(16) status "PENDING, PUBLISHED, FAILED"
        INT retry_count
        TIMESTAMPTZ created_at
        TIMESTAMPTZ published_at
    }

    IDEMPOTENCY_RECORDS {
        VARCHAR(128) idempotency_key PK
        VARCHAR(128) request_hash
        TEXT response_body
        VARCHAR(16) status "IN_PROGRESS, COMPLETED, FAILED"
        TIMESTAMPTZ created_at
        TIMESTAMPTZ expires_at
    }
```

---

## 4. Authentication & Authorization Flow

The application implements a stateless dual-credential security filter chain supporting API Keys and Bearer JWTs.

### Diagram 4: Security Filter Chain & RBAC Flow

```mermaid
sequenceDiagram
    autonumber
    actor Client as Client / Browser
    participant Chain as SecurityFilterChain
    participant APIKeyFilter as ApiKeyAuthenticationFilter
    participant JWTFilter as MockJwtAuthenticationFilter
    participant SecCtx as SecurityContextHolder
    participant Endpoint as REST Controller (/api/v1/*)

    Client->>Chain: HTTP Request (Headers: Authorization or X-API-KEY)
    
    Note over Chain: Check Public Endpoints (/index.html, /swagger-ui/**)
    alt Is Public Endpoint
        Chain->>Endpoint: Permit Unrestricted Access
    else Protected Endpoint
        Chain->>APIKeyFilter: Check X-API-KEY Header
        alt X-API-KEY Present
            alt Key contains 'admin' or 'finance'
                APIKeyFilter->>SecCtx: Set Auth (ROLE_FINANCE_ADMIN, ROLE_USER)
            else Standard Client Key
                APIKeyFilter->>SecCtx: Set Auth (ROLE_USER)
            end
        else No API Key
            Chain->>JWTFilter: Check Authorization: Bearer <token>
            alt Bearer Token Present
                JWTFilter->>JWTFilter: Parse Base64 Payload & Extract Roles
                JWTFilter->>SecCtx: Set Auth (Extracted Roles or Mock Roles)
            else No Token
                Note over SecCtx: Remains Unauthenticated
            end
        end

        Chain->>Chain: Authorization Verification
        alt Unauthenticated Request to Protected Route
            Chain-->>Client: 401 Unauthorized (CustomAuthenticationEntryPoint)
        else Role Insufficient (e.g. Non-Admin on /reconcile)
            Chain-->>Client: 403 Forbidden (CustomAccessDeniedHandler)
        else Authorized
            Chain->>Endpoint: Dispatch to Controller Handler
        end
    end
```

---

## 5. Complete End-to-End Request Lifecycle (Money Transfer)

This diagram tracks the lifecycle of `POST /api/v1/payments` from the browser DOM event to database commit and Kafka event receipt.

### Diagram 5: Complete Money Transfer Request Lifecycle

```mermaid
sequenceDiagram
    autonumber
    actor Browser as User / Web Client
    participant Controller as PaymentController
    participant Svc as PaymentApplicationService
    participant IdemSvc as IdempotencyKeyService
    participant Redis as Redis (SETNX)
    participant Postgres as PostgreSQL (ACID)
    participant Domain as DoubleEntryLedger
    participant Outbox as OutboxPublisher
    participant Kafka as Apache Kafka

    Browser->>Controller: POST /api/v1/payments (Payload + Idempotency-Key)
    Controller->>Controller: Jakarta Validation (@Valid @NotNull @Positive)
    Controller->>Svc: processPayment(command)

    rect rgb(240, 248, 255)
        Note over Svc,Redis: Phase 1: Two-Tier Idempotency Evaluation
        Svc->>IdemSvc: checkAndLock(key, payload)
        IdemSvc->>Postgres: SELECT * FROM idempotency_records WHERE key = ?
        alt Record Exists (COMPLETED)
            IdemSvc-->>Svc: Return Cached Response Body
            Svc-->>Controller: Return Deserialized Payment
            Controller-->>Browser: HTTP 200 OK (Cached Payment)
        else Key In Flight
            IdemSvc-->>Svc: Conflict
            Svc-->>Browser: HTTP 409 Conflict (In-Flight)
        else New Key
            IdemSvc->>Redis: SETNX idempotency:lock:<key> <hash> EX 30
            Redis-->>IdemSvc: OK (Acquired)
            IdemSvc-->>Svc: PROCEED
        end
    end

    rect rgb(255, 250, 240)
        Note over Svc,Postgres: Phase 2: Transactional Core (@Transactional)
        Svc->>Postgres: INSERT INTO idempotency_records (status='IN_PROGRESS')
        Svc->>Postgres: INSERT INTO payments (status='PENDING')
        
        Note over Svc: Deadlock Avoidance: Order UUIDs lexicographically
        Svc->>Postgres: SELECT * FROM accounts WHERE id = firstId FOR UPDATE
        Svc->>Postgres: SELECT * FROM accounts WHERE id = secondId FOR UPDATE
        
        Svc->>Domain: postTransfer(paymentId, source, dest, amount)
        Domain->>Domain: source.debit(amount), dest.credit(amount)
        Domain->>Domain: Verify invariant (debit == credit)
        Domain-->>Svc: PostingResult(debitEntry, creditEntry)

        Svc->>Postgres: UPDATE accounts SET balance = ... WHERE id = firstId
        Svc->>Postgres: UPDATE accounts SET balance = ... WHERE id = secondId
        Svc->>Postgres: INSERT INTO ledger_entries (DEBIT leg)
        Svc->>Postgres: INSERT INTO ledger_entries (CREDIT leg)
        Svc->>Postgres: UPDATE payments SET status = 'COMPLETED'
        Svc->>Postgres: INSERT INTO outbox_events (PAYMENT_COMPLETED, status='PENDING')
        Svc->>Postgres: UPDATE idempotency_records SET status = 'COMPLETED', response_body = ...
        Svc->>Redis: DEL idempotency:lock:<key>
        Postgres-->>Svc: Transaction COMMIT (ACID Guaranteed)
    end

    Svc-->>Controller: PaymentResponse
    Controller-->>Browser: HTTP 201 Created (Payment JSON)

    rect rgb(245, 255, 245)
        Note over Outbox,Kafka: Phase 3: Asynchronous Outbox Relay (@Scheduled)
        Outbox->>Postgres: SELECT * FROM outbox_events WHERE status='PENDING' FOR UPDATE SKIP LOCKED
        Outbox->>Kafka: send('payment.events', key=paymentId, event)
        Kafka-->>Outbox: RecordMetadata (Ack from Broker)
        Outbox->>Postgres: UPDATE outbox_events SET status='PUBLISHED'
    end
```

---

## 6. Payment & Account State Transition Lifecycle

### Diagram 6: State Transition State Machines

```mermaid
stateDiagram-v2
    direction LR

    state "Payment Lifecycle" as PaymentState {
        [*] --> PENDING : Payment.create()
        PENDING --> COMPLETED : Debit & Credit Committed
        PENDING --> FAILED : Insufficient Funds / Currency Mismatch
        COMPLETED --> REFUNDED : refundPayment() executed
        FAILED --> [*]
        REFUNDED --> [*]
    }

    state "Account Lifecycle" as AccountState {
        [*] --> ACTIVE : openAccount()
        ACTIVE --> FROZEN : freeze()
        FROZEN --> ACTIVE : activate()
        ACTIVE --> CLOSED : close() (Requires Balance == 0)
        FROZEN --> CLOSED : close() (Requires Balance == 0)
        CLOSED --> [*]
    }

    state "Outbox Event Lifecycle" as OutboxState {
        [*] --> PENDING_EVENT : Written in Payment TX
        PENDING_EVENT --> PUBLISHED : Kafka Ack Received
        PENDING_EVENT --> FAILED_EVENT : Kafka Producer Timeout
        FAILED_EVENT --> PENDING_EVENT : Retry Worker (retry_count < 10)
        PUBLISHED --> [*]
    }
```

---

## 7. Containerized Deployment Topology

### Diagram 7: Docker Compose Deployment Topology

```mermaid
flowchart TD
    subgraph Host_Machine["Host Machine (Windows/Linux)"]
        BrowserClient["Browser (localhost:8081)"]
        KafkaUIClient["Kafka UI (localhost:8085)"]
        PostgresPort["localhost:5433"]
        RedisPort["localhost:6380"]
        KafkaPort["localhost:9092"]
    end

    subgraph Docker_Bridge["Docker Internal Network ('payment-ledger-network')"]
        subgraph App_Container["Container: payment-ledger-app"]
            SpringApp["Spring Boot (Port 8080)"]
            JVM["Eclipse Temurin 17 JRE Alpine (Non-Root User)"]
        end

        subgraph Postgres_Container["Container: payment-ledger-postgres"]
            PostgresEngine["PostgreSQL 15 Engine (Port 5432)"]
            PGVol[("Volume: pgdata")]
        end

        subgraph Redis_Container["Container: payment-ledger-redis"]
            RedisEngine["Redis 7 In-Memory Server (Port 6379)"]
            RedisVol[("Volume: redisdata")]
        end

        subgraph Kafka_Container["Container: payment-ledger-kafka"]
            KafkaBrokerEngine["Apache Kafka 3.7 KRaft Broker (Port 9092)"]
            KafkaVol[("Volume: kafkadata")]
        end

        subgraph KafkaUI_Container["Container: payment-ledger-kafka-ui"]
            KafkaUIEngine["Provectus Kafka UI (Port 8080)"]
        end
    end

    BrowserClient -->|"Port 8081 -> 8080"| SpringApp
    KafkaUIClient -->|"Port 8085 -> 8080"| KafkaUIEngine
    PostgresPort -->|"Port 5433 -> 5432"| PostgresEngine
    RedisPort -->|"Port 6380 -> 6379"| RedisEngine
    KafkaPort -->|"Port 9092 -> 9092"| KafkaBrokerEngine

    SpringApp -->|"jdbc:postgresql://postgres:5432/payment_ledger"| PostgresEngine
    SpringApp -->|"redis:6379"| RedisEngine
    SpringApp -->|"kafka:9092"| KafkaBrokerEngine
    KafkaUIEngine -->|"kafka:9092"| KafkaBrokerEngine
    PostgresEngine --- PGVol
    RedisEngine --- RedisVol
    KafkaBrokerEngine --- KafkaVol
```
