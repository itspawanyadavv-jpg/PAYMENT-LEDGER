-- Initial Flyway Migration: Double-Entry Payment Ledger Schema

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

CREATE INDEX IF NOT EXISTS idx_accounts_status ON accounts(status);
CREATE INDEX IF NOT EXISTS idx_accounts_system ON accounts(is_system_account);

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

CREATE INDEX IF NOT EXISTS idx_payments_idempotency_key ON payments(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_payments_source_account ON payments(source_account_id);
CREATE INDEX IF NOT EXISTS idx_payments_dest_account ON payments(destination_account_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);

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

CREATE INDEX IF NOT EXISTS idx_ledger_account ON ledger_entries(account_id);
CREATE INDEX IF NOT EXISTS idx_ledger_payment ON ledger_entries(payment_id);
CREATE INDEX IF NOT EXISTS idx_ledger_created_at ON ledger_entries(created_at DESC);

CREATE TABLE IF NOT EXISTS outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING / PUBLISHED / FAILED
    retry_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox_events(status, created_at);

CREATE TABLE IF NOT EXISTS idempotency_records (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_hash    VARCHAR(128) NOT NULL,
    response_body   TEXT,
    status          VARCHAR(16) NOT NULL, -- IN_PROGRESS / COMPLETED / FAILED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
);

-- Seed Initial System Reserve Clearing Accounts
INSERT INTO accounts (id, account_number, owner_name, currency, balance, status, is_system_account, version, created_at, updated_at)
VALUES 
  ('00000000-0000-0000-0000-000000000001', 'SYSTEM_RESERVE_USD', 'Central Reserve (USD)', 'USD', 1000000000.0000, 'ACTIVE', TRUE, 0, now(), now()),
  ('00000000-0000-0000-0000-000000000002', 'SYSTEM_RESERVE_EUR', 'Central Reserve (EUR)', 'EUR', 1000000000.0000, 'ACTIVE', TRUE, 0, now(), now()),
  ('00000000-0000-0000-0000-000000000003', 'SYSTEM_RESERVE_GBP', 'Central Reserve (GBP)', 'GBP', 1000000000.0000, 'ACTIVE', TRUE, 0, now(), now())
ON CONFLICT (account_number) DO NOTHING;
