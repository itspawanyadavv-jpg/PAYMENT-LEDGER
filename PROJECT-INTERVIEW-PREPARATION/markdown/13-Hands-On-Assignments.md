# 13. Hands-On Code Ownership & Coding Assignments

> **Objective:** Hands-on mastery of your own repository. Solve these 8 targeted coding challenges to build deep muscle memory before your technical interview.

---

## Assignment 1: Implement Daily Transfer Limit Validation

### Business Requirement:
To comply with anti-money laundering (AML) and fraud prevention rules, an account should not be allowed to transfer more than **$10,000.00 USD total** within a rolling 24-hour window.

### Where to Implement:
- In `domain.model.Account.java` or `application.service.PaymentApplicationService.java`.
- Create a new outbound repository method in `LedgerEntryRepositoryPort.java`:
  ```java
  Money calculateDailyDebitedAmount(UUID accountId, Instant since);
  ```

### Acceptance Criteria:
1. If the current payment amount plus the last 24 hours of debits exceeds `$10,000.00`, throw a custom `DailyLimitExceededException`.
2. Map `DailyLimitExceededException` in `GlobalExceptionHandler.java` to `HTTP 422 Unprocessable Entity`.
3. Write a unit test in `ConcurrentPaymentTest.java` verifying that the 11th transfer exceeding $10,000 is rejected while previous valid transfers succeed.

---

## Assignment 2: Cryptographic JWT Signature Verification

### Security Requirement:
Replace the mock Base64 decoding in `MockJwtAuthenticationFilter.java` with a real cryptographic HMAC-SHA256 signature verification.

### Starter Snippet:
```java
// Hint: Use javax.crypto.Mac or jjwt library
public static boolean verifyHmacSha256(String data, String signature, String secret) {
    Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
    SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    sha256_HMAC.init(secret_key);
    byte[] rawHmac = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
    String computedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
    return MessageDigest.isEqual(signature.getBytes(), computedSignature.getBytes());
}
```

### Acceptance Criteria:
1. If the signature is invalid or tampered with, the filter immediately sets the response status to `401 Unauthorized` and returns.
2. Update `SecurityTest.java` to test both a valid signed token and an altered payload token.

---

## Assignment 3: Dead-Letter Queue (DLQ) for Failed Outbox Events

### Resilience Requirement:
In `OutboxPublisher.java`, if an outbox event fails to publish to Kafka after **5 retry attempts**, it should be moved to a dead-letter state rather than repeatedly retrying indefinitely.

### Where to Implement:
- In `outbox_events` table / `OutboxEventJpaEntity.java`:
  - Add status `DEAD_LETTER`.
- In `OutboxPublisher.java`:
  - When `event.getRetryCount() >= 5`, update status to `DEAD_LETTER` and emit an alert log.
  - Publish the poison event to a Kafka DLQ topic: `payment.events.dlq`.

---

## Assignment 4: Paginated Historical Statement Query

### Requirement:
Allow customer clients to retrieve paginated transaction history with date filters.

### Where to Implement:
- Add a new endpoint to `LedgerController.java`:
  ```java
  @GetMapping("/accounts/{accountId}/statement")
  public ResponseEntity<Page<LedgerEntryResponse>> getStatement(
          @PathVariable UUID accountId,
          @RequestParam(required = false) Instant startDate,
          @RequestParam(required = false) Instant endDate,
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable)
  ```
- Implement the query using Spring Data JPA Specification or a `@Query` in `SpringDataLedgerEntryRepository.java`.

---

## Assignment 5: Fix Frontend Admin Reconciliation Header

### Bug Fix Requirement:
In `src/main/resources/static/app.js` line 443, fix the reconciliation fetch call so that clicking "Run Invariant Audit Now" in the browser dashboard successfully authenticates against the backend.

### Task:
Update `runReconciliation` in `app.js` to pass `headers: { 'X-API-KEY': 'finance-admin-key' }`. Test it locally and verify that the UI displays a green status badge.

---

## Assignment 6: Multi-Currency Transfer & Exchange Rate Provider

### Architecture Challenge:
Currently, transfers between different currencies throw `CurrencyMismatchException`.
Design and implement an `ExchangeRateProvider` port:
```java
public interface ExchangeRateProviderPort {
    BigDecimal getRate(String fromCurrency, String toCurrency);
}
```
1. When Alice transfers `100.00 USD` to Bob's `EUR` account with exchange rate `0.92`:
   - Alice is debited `100.00 USD`.
   - Bob is credited `92.00 EUR`.
2. How does the double-entry invariant hold?
   - *FinTech Solution:* Balance both legs through a `SYSTEM_FX_CLEARING` account (`DEBIT Alice 100 USD`, `CREDIT FX_RESERVE 100 USD`, `DEBIT FX_RESERVE 92 EUR`, `CREDIT Bob 92 EUR`).

---

## Assignment 7: Predict the Output & Find the Bug Challenge

### Look at this hypothetical code snippet:
```java
@Transactional
public void fundAccount(UUID accountId, Money amount) {
    Account account = accountRepository.findById(accountId).orElseThrow();
    account.credit(amount);
    // Note: Developer forgot to call accountRepository.save(account);
}
```

### Questions for You to Answer:
1. **Does the database update the account balance when this method completes?**  
   *(Answer: YES! In JPA/Hibernate, entities loaded within a `@Transactional` boundary are managed in the Persistence Context. Hibernate's dirty-checking mechanism automatically detects that `account.balance` changed and executes an `UPDATE` statement on transaction commit! Calling `.save()` is technically redundant).*
2. **What potential bug exists here under concurrent traffic?**  
   *(Answer: The method used `findById` instead of `findByIdForUpdate`. Two concurrent calls will read the same balance and execute conflicting updates! It must use pessimistic locking).*

---

## Assignment 8: Write a Custom Mockito Unit Test for `PaymentApplicationService`

### Task:
Write a pure unit test in `src/test/java/com/aman/payment/test/PaymentApplicationServiceTest.java` that mocks all 5 outbound ports (`PaymentRepositoryPort`, `AccountRepositoryPort`, etc.) using Mockito:
1. Mock `accountRepository.findByIdForUpdate` to return two valid mock accounts.
2. Mock `idempotencyPort.evaluate` to return `Outcome.PROCEED`.
3. Verify that `paymentRepository.save` is invoked exactly twice (once for `PENDING`, once for `COMPLETED`).
4. Verify that `paymentEventPublisher.paymentCompleted` is invoked with the completed payment entity.
