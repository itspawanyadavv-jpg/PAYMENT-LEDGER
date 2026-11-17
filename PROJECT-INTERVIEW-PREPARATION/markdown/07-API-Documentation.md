# 07. Complete API Inventory & REST Specification

> **Scope:** Full contract specification of all 11 REST endpoints across Accounts, Payments, and Ledger, including headers, request/response JSON, database operations, error codes, and curl/Postman commands.

---

## 1. Complete API Inventory Matrix

| # | HTTP Method | Route / Endpoint | Controller Method | Required Role | Summary |
|---|---|---|---|---|---|
| **1** | `POST` | `/api/v1/payments` | `PaymentController.createPayment()` | `ROLE_USER` | Initiate idempotent double-entry transfer |
| **2** | `GET` | `/api/v1/payments` | `PaymentController.getAllPayments()` | `ROLE_USER` | List all recent transfers |
| **3** | `GET` | `/api/v1/payments/{paymentId}` | `PaymentController.getPayment()` | `ROLE_USER` | Get payment details by UUID |
| **4** | `GET` | `/api/v1/payments?idempotencyKey=...` | `PaymentController.getByIdempotencyKey()` | `ROLE_USER` | Get payment by idempotency key |
| **5** | `POST` | `/api/v1/payments/{paymentId}/refund`| `PaymentController.refundPayment()` | `ROLE_USER` | Refund completed payment (reverses legs) |
| **6** | `POST` | `/api/v1/accounts` | `AccountController.openAccount()` | `ROLE_USER` | Open new customer account |
| **7** | `GET` | `/api/v1/accounts` | `AccountController.getAllAccounts()` | `ROLE_USER` | List all customer accounts |
| **8** | `GET` | `/api/v1/accounts/{accountId}` | `AccountController.getAccount()` | `ROLE_USER` | Get account details by ID |
| **9** | `POST` | `/api/v1/accounts/{id}/deposit` | `AccountController.deposit()` | `ROLE_USER` | Deposit liquidity from Central Reserve |
| **10**| `POST` | `/api/v1/accounts/{id}/withdraw` | `AccountController.withdraw()` | `ROLE_USER` | Withdraw funds to Central Reserve |
| **11**| `PATCH`| `/api/v1/accounts/{id}/status` | `AccountController.updateStatus()` | `ROLE_USER` | Update status (`ACTIVE`, `FROZEN`, `CLOSED`) |
| **12**| `GET` | `/api/v1/ledger/entries?limit=50` | `LedgerController.getRecentEntries()` | `ROLE_USER` | Stream recent immutable ledger entries |
| **13**| `GET` | `/api/v1/ledger/accounts/{id}/entries` | `LedgerController.getAccountHistory()` | `ROLE_USER` | Audit history for a specific account |
| **14**| `GET` | `/api/v1/ledger/payments/{id}/entries` | `LedgerController.getPaymentEntries()` | `ROLE_USER` | Get debit/credit legs for a payment |
| **15**| `GET` | `/api/v1/ledger/payments/{id}/balanced`| `LedgerController.isBalanced()` | `ROLE_USER` | Check if a payment nets to zero |
| **16**| `POST` | `/api/v1/ledger/reconcile` | `LedgerController.reconcile()` | `ROLE_FINANCE_ADMIN` | Trigger on-demand invariant audit |

---

## 2. Forensic Specification of Key Endpoints

### Endpoint 1: Initiate Payment Transfer
- **`POST /api/v1/payments`**
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer mock-user-token` (or `X-API-KEY: test-key`)
- **Request Body (`PaymentRequest`):**
  ```json
  {
    "idempotencyKey": "pay-f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "sourceAccountId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "destinationAccountId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "amount": "150.00",
    "currency": "USD"
  }
  ```
- **Validation Constraints:**
  - `idempotencyKey`: `@NotBlank`
  - `sourceAccountId`: `@NotNull`
  - `destinationAccountId`: `@NotNull`
  - `amount`: `@NotNull @Positive`
  - `currency`: `@NotBlank @Size(min=3, max=3)`
- **Response Body (`PaymentResponse` - 201 Created):**
  ```json
  {
    "paymentId": "c3d4e5f6-a7b8-9012-cdef-123456789012",
    "idempotencyKey": "pay-f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "sourceAccountId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "destinationAccountId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "amount": 150.0000,
    "currency": "USD",
    "status": "COMPLETED",
    "createdAt": "2026-11-16T10:15:30.123456Z"
  }
  ```
- **Database Operations Executed:**
  1. `SELECT ... FROM idempotency_records WHERE idempotency_key = ?`
  2. `INSERT INTO idempotency_records (status='IN_PROGRESS')`
  3. `INSERT INTO payments (status='PENDING')`
  4. `SELECT ... FROM accounts WHERE id = ? FOR UPDATE` (source & destination in sorted UUID order)
  5. `UPDATE accounts SET balance = balance - 150.00 WHERE id = sourceId`
  6. `UPDATE accounts SET balance = balance + 150.00 WHERE id = destId`
  7. `INSERT INTO ledger_entries (DEBIT)`
  8. `INSERT INTO ledger_entries (CREDIT)`
  9. `UPDATE payments SET status = 'COMPLETED'`
  10. `INSERT INTO outbox_events (PAYMENT_COMPLETED)`
  11. `UPDATE idempotency_records SET status = 'COMPLETED'`
- **Error Responses:**
  - `400 Bad Request`: Validation error or currency mismatch.
  - `404 Not Found`: Source or destination account does not exist.
  - `409 Conflict`: Duplicate payment in flight or account inactive/frozen.
  - `422 Unprocessable Entity`: Insufficient balance in source account.

---

### Endpoint 2: Open Customer Account
- **`POST /api/v1/accounts`**
- **Request Body (`OpenAccountRequest`):**
  ```json
  {
    "accountNumber": "ACC-9001",
    "ownerName": "Alice Johnson",
    "currency": "USD",
    "openingBalance": "1000.00"
  }
  ```
- **Response Body (`AccountResponse` - 201 Created):**
  ```json
  {
    "accountId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "accountNumber": "ACC-9001",
    "ownerName": "Alice Johnson",
    "currency": "USD",
    "balance": 1000.0000,
    "status": "ACTIVE",
    "systemAccount": false,
    "createdAt": "2026-11-16T10:00:00Z"
  }
  ```
- **FinTech Detail:** If `openingBalance > 0`, the service automatically generates an internal transfer from `SYSTEM_RESERVE_USD` to Alice's account, preserving global ledger integrity.

---

### Endpoint 3: Refund Payment
- **`POST /api/v1/payments/{paymentId}/refund`**
- **Request Body (`RefundRequest`):**
  ```json
  {
    "idempotencyKey": "refund-pay-f47ac10b-58cc"
  }
  ```
- **Execution Logic:**
  - Asserts original payment is `COMPLETED`.
  - Creates a reversing payment where `source = original.destination` and `dest = original.source`.
  - Marks original payment as `REFUNDED`.

---

### Endpoint 4: Trigger On-Demand Reconciliation Audit
- **`POST /api/v1/ledger/reconcile`**
- **Authorization:** Requires `ROLE_FINANCE_ADMIN` (Header: `X-API-KEY: finance-admin-key` or Bearer token with finance admin claim).
- **Response Body (200 OK - No Discrepancies):**
  ```json
  []
  ```
- **Response Body (200 OK - Drift Detected):**
  ```json
  [
    {
      "type": "ACCOUNT_BALANCE_DRIFT",
      "referenceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "detail": "Account ACC-9001 cached balance 1000.0000 does not match ledger sum 950.0000 (drift: 50.0000)"
    }
  ]
  ```

---

## 3. Postman / cURL Ready Command Suite

### 1. Health & Invariants Check (Public):
```bash
curl -X GET http://localhost:8081/actuator/health
```

### 2. Create Account:
```bash
curl -X POST http://localhost:8081/api/v1/accounts \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: standard-user-key" \
  -d '{
    "accountNumber": "ACC-777",
    "ownerName": "Satoshi Nakamoto",
    "currency": "USD",
    "openingBalance": 500.00
  }'
```

### 3. Execute Money Transfer:
```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: standard-user-key" \
  -d '{
    "idempotencyKey": "pay-unique-001",
    "sourceAccountId": "<SOURCE_UUID>",
    "destinationAccountId": "<DEST_UUID>",
    "amount": 75.50,
    "currency": "USD"
  }'
```

### 4. Trigger Invariant Reconciliation (Admin Key):
```bash
curl -X POST http://localhost:8081/api/v1/ledger/reconcile \
  -H "X-API-KEY: finance-admin-key"
```
*(If called without the key, returns `401 Unauthorized` or `403 Forbidden`)*.
