package com.aman.payment.service;

import com.aman.payment.dto.PaymentRequest;
import com.aman.payment.dto.PaymentResponse;
import com.aman.payment.entity.Payment;
import com.aman.payment.exception.PaymentNotFoundException;
import com.aman.payment.kafka.PaymentEventProducer;
import com.aman.payment.ledger.DoubleEntryLedger;
import com.aman.payment.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates the full lifecycle of a payment:
 *
 *   1. Idempotency check/lock (outside the DB transaction - see
 *      IdempotencyService) so duplicate requests short-circuit before
 *      any business work happens.
 *   2. A single DB transaction that: creates the Payment row, posts
 *      both ledger legs via DoubleEntryLedger (debit + credit, locked
 *      in deadlock-safe order), and writes an outbox event - all
 *      atomically. Either the whole payment "happened" or none of it
 *      did.
 *   3. Idempotency record finalization (COMPLETED/FAILED) tied to the
 *      same outcome.
 *
 * Concurrency: row-level locks (see AccountRepository.findByIdForUpdate)
 * mean concurrent payments touching the same account serialize rather
 * than corrupt each other. Lock contention can still surface as
 * CannotAcquireLockException (lock wait timeout) which we retry a
 * bounded number of times with backoff before giving up.
 */
@Service
@Slf4j
public class PaymentService {

    private static final int MAX_RETRIES = 3;

    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;
    private final LedgerService ledgerService;
    private final PaymentEventProducer paymentEventProducer;
    private final com.aman.payment.repository.AccountRepository accountRepository;

    // Self-injected proxy so processInTransaction's @Transactional annotation
    // is honored. A plain `this.processInTransaction(...)` call would bypass
    // Spring's transactional proxy entirely (a classic self-invocation
    // pitfall) and silently run without a transaction.
    private final PaymentService self;

    public PaymentService(PaymentRepository paymentRepository,
                           IdempotencyService idempotencyService,
                           LedgerService ledgerService,
                           PaymentEventProducer paymentEventProducer,
                           com.aman.payment.repository.AccountRepository accountRepository,
                           @Lazy PaymentService self) {
        this.paymentRepository = paymentRepository;
        this.idempotencyService = idempotencyService;
        this.ledgerService = ledgerService;
        this.paymentEventProducer = paymentEventProducer;
        this.accountRepository = accountRepository;
        this.self = self;
    }

    public PaymentResponse executePayment(PaymentRequest request) {
        IdempotencyService.Decision decision = idempotencyService.evaluate(request);

        switch (decision.outcome()) {
            case RETURN_CACHED -> {
                return decision.cachedResponse()
                        .orElseThrow(() -> new IllegalStateException(
                                "Idempotency record marked completed but no cached response found"));
            }
            case CONFLICT_IN_FLIGHT -> throw new IllegalStateException(
                    "A payment with idempotency key " + request.getIdempotencyKey() + " is already being processed");
            case PROCEED -> {
                // fall through to processing below
            }
        }

        try {
            PaymentResponse response = processWithRetry(request);
            return response;
        } catch (RuntimeException e) {
            idempotencyService.fail(request.getIdempotencyKey());
            throw e;
        }
    }

    private PaymentResponse processWithRetry(PaymentRequest request) {
        int attempt = 0;
        while (true) {
            try {
                return self.processInTransaction(request);
            } catch (OptimisticLockingFailureException | CannotAcquireLockException e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    throw e;
                }
                log.warn("Retrying payment {} after lock conflict (attempt {}/{})",
                        request.getIdempotencyKey(), attempt, MAX_RETRIES);
                backoff(attempt);
            }
        }
    }

    @Transactional
    public PaymentResponse processInTransaction(PaymentRequest request) {
        if (request.getSourceAccountId().equals(request.getDestinationAccountId())) {
            throw new IllegalArgumentException("Source and destination accounts must be distinct");
        }

        idempotencyService.begin(request);

        Payment payment = Payment.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .sourceAccountId(request.getSourceAccountId())
                .destinationAccountId(request.getDestinationAccountId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(Payment.Status.PENDING)
                .build();
        payment = paymentRepository.save(payment);
        paymentEventProducer.paymentInitiated(payment);

        try {
            ledgerService.post(
                    payment.getId(),
                    request.getSourceAccountId(),
                    request.getDestinationAccountId(),
                    request.getAmount(),
                    request.getCurrency());

            payment.markCompleted();
            payment = paymentRepository.save(payment);
            paymentEventProducer.paymentCompleted(payment);

            PaymentResponse response = PaymentResponse.from(payment);
            idempotencyService.complete(request.getIdempotencyKey(), response);
            return response;

        } catch (RuntimeException businessException) {
            // Business failures (e.g. insufficient balance) roll back the
            // whole transaction - including the Payment insert and the
            // PAYMENT_INITIATED outbox event - so no partial ledger state
            // or half-completed payment ever becomes visible. The
            // idempotency key is marked FAILED by executePayment's catch
            // block, which allows the same key to be retried later (e.g.
            // once the account has been topped up).
            throw businessException;
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByIdempotencyKey(String idempotencyKey) {
        Payment payment = paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new PaymentNotFoundException(idempotencyKey));
        return PaymentResponse.from(payment);
    }

    @Transactional
    public com.aman.payment.entity.Account getOrCreateSystemAccount(String currency) {
        String sysAccNum = "SYSTEM_RESERVE_" + currency.toUpperCase();
        return accountRepository.findByAccountNumber(sysAccNum)
                .orElseGet(() -> accountRepository.save(com.aman.payment.entity.Account.builder()
                        .accountNumber(sysAccNum)
                        .ownerName("Central Reserve (" + currency.toUpperCase() + ")")
                        .currency(currency.toUpperCase())
                        .balance(new java.math.BigDecimal("1000000000.0000"))
                        .systemAccount(true)
                        .status(com.aman.payment.entity.Account.Status.ACTIVE)
                        .build()));
    }

    public PaymentResponse deposit(UUID accountId, java.math.BigDecimal amount, String idempotencyKey) {
        com.aman.payment.entity.Account dest = accountRepository.findById(accountId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Account not found: " + accountId));
        com.aman.payment.entity.Account sys = getOrCreateSystemAccount(dest.getCurrency());
        return executePayment(PaymentRequest.builder()
                .idempotencyKey(idempotencyKey)
                .sourceAccountId(sys.getId())
                .destinationAccountId(dest.getId())
                .amount(amount)
                .currency(dest.getCurrency())
                .build());
    }

    public PaymentResponse withdraw(UUID accountId, java.math.BigDecimal amount, String idempotencyKey) {
        com.aman.payment.entity.Account src = accountRepository.findById(accountId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Account not found: " + accountId));
        com.aman.payment.entity.Account sys = getOrCreateSystemAccount(src.getCurrency());
        return executePayment(PaymentRequest.builder()
                .idempotencyKey(idempotencyKey)
                .sourceAccountId(src.getId())
                .destinationAccountId(sys.getId())
                .amount(amount)
                .currency(src.getCurrency())
                .build());
    }

    @Transactional
    public PaymentResponse refundPayment(UUID originalPaymentId, String idempotencyKey) {
        Payment original = paymentRepository.findById(originalPaymentId)
                .orElseThrow(() -> new PaymentNotFoundException(originalPaymentId));

        if (original.getStatus() != Payment.Status.COMPLETED) {
            throw new IllegalStateException("Only COMPLETED payments can be refunded. Current status: " + original.getStatus());
        }

        PaymentRequest refundRequest = PaymentRequest.builder()
                .idempotencyKey(idempotencyKey)
                .sourceAccountId(original.getDestinationAccountId())
                .destinationAccountId(original.getSourceAccountId())
                .amount(original.getAmount())
                .currency(original.getCurrency())
                .build();

        PaymentResponse response = executePayment(refundRequest);
        original.setStatus(Payment.Status.REFUNDED);
        paymentRepository.save(original);
        return response;
    }

    @Transactional(readOnly = true)
    public java.util.List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(PaymentResponse::from)
                .toList();
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(50L * (1L << attempt));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
