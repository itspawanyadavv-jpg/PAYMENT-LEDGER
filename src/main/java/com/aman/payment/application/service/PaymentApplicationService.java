package com.aman.payment.application.service;

import com.aman.payment.application.port.in.GetPaymentUseCase;
import com.aman.payment.application.port.in.ProcessPaymentUseCase;
import com.aman.payment.application.port.in.RefundPaymentUseCase;
import com.aman.payment.application.port.out.AccountRepositoryPort;
import com.aman.payment.application.port.out.IdempotencyPort;
import com.aman.payment.application.port.out.LedgerEntryRepositoryPort;
import com.aman.payment.application.port.out.PaymentEventPublisherPort;
import com.aman.payment.application.port.out.PaymentRepositoryPort;
import com.aman.payment.domain.exception.PaymentNotFoundException;
import com.aman.payment.domain.model.Account;
import com.aman.payment.domain.model.DoubleEntryLedger;
import com.aman.payment.domain.model.Payment;
import com.aman.payment.domain.model.PaymentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Orchestrates payment processing using Hexagonal Architecture ports and rich domain models.
 */
@Service
@Slf4j
public class PaymentApplicationService implements ProcessPaymentUseCase, GetPaymentUseCase, RefundPaymentUseCase {

    private static final int MAX_RETRIES = 3;

    private final PaymentRepositoryPort paymentRepository;
    private final AccountRepositoryPort accountRepository;
    private final LedgerEntryRepositoryPort ledgerEntryRepository;
    private final PaymentEventPublisherPort paymentEventPublisher;
    private final IdempotencyPort idempotencyPort;
    private final DoubleEntryLedger doubleEntryLedger;
    private final ObjectMapper objectMapper;
    private final PaymentApplicationService self;

    public PaymentApplicationService(
            PaymentRepositoryPort paymentRepository,
            AccountRepositoryPort accountRepository,
            LedgerEntryRepositoryPort ledgerEntryRepository,
            PaymentEventPublisherPort paymentEventPublisher,
            IdempotencyPort idempotencyPort,
            ObjectMapper objectMapper,
            @Lazy PaymentApplicationService self) {
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.paymentEventPublisher = paymentEventPublisher;
        this.idempotencyPort = idempotencyPort;
        this.doubleEntryLedger = new DoubleEntryLedger();
        this.objectMapper = objectMapper;
        this.self = self;
    }

    @Override
    public Payment processPayment(ProcessPaymentCommand command) {
        String payload = serialize(command);
        IdempotencyPort.Decision decision = idempotencyPort.evaluate(command.idempotencyKey(), payload);

        switch (decision.outcome()) {
            case RETURN_CACHED -> {
                String cachedJson = decision.cachedResponseBody()
                        .orElseThrow(() -> new IllegalStateException("Idempotency marked complete but cached body missing"));
                return deserializePayment(cachedJson);
            }
            case CONFLICT_IN_FLIGHT -> throw new IllegalStateException(
                    "A payment with idempotency key " + command.idempotencyKey() + " is currently being processed");
            case PROCEED -> {
                // proceed to transactional processing
            }
        }

        try {
            return processWithRetry(command, payload);
        } catch (RuntimeException e) {
            idempotencyPort.fail(command.idempotencyKey());
            throw e;
        }
    }

    private Payment processWithRetry(ProcessPaymentCommand command, String payload) {
        int attempt = 0;
        while (true) {
            try {
                return self.executeInTransaction(command, payload);
            } catch (OptimisticLockingFailureException | CannotAcquireLockException e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    throw e;
                }
                log.warn("Retrying payment {} after lock conflict (attempt {}/{})",
                        command.idempotencyKey(), attempt, MAX_RETRIES);
                backoff(attempt);
            }
        }
    }

    @Transactional
    public Payment executeInTransaction(ProcessPaymentCommand command, String payload) {
        idempotencyPort.begin(command.idempotencyKey(), payload);

        Payment payment = Payment.create(
                command.idempotencyKey(),
                command.sourceAccountId(),
                command.destinationAccountId(),
                command.amount()
        );
        payment = paymentRepository.save(payment);
        paymentEventPublisher.paymentInitiated(payment);

        try {
            // Deadlock avoidance: lock accounts in a fixed UUID order
            UUID firstId = command.sourceAccountId().compareTo(command.destinationAccountId()) <= 0
                    ? command.sourceAccountId() : command.destinationAccountId();
            UUID secondId = firstId.equals(command.sourceAccountId())
                    ? command.destinationAccountId() : command.sourceAccountId();

            Account firstAccount = accountRepository.findByIdForUpdate(firstId)
                    .orElseThrow(() -> new NoSuchElementException("Account not found: " + firstId));
            Account secondAccount = accountRepository.findByIdForUpdate(secondId)
                    .orElseThrow(() -> new NoSuchElementException("Account not found: " + secondId));

            Account sourceAccount = firstAccount.getId().equals(command.sourceAccountId()) ? firstAccount : secondAccount;
            Account destinationAccount = firstAccount.getId().equals(command.destinationAccountId()) ? firstAccount : secondAccount;

            // Execute double-entry transfer natively on the rich domain model
            DoubleEntryLedger.PostingResult result = doubleEntryLedger.postTransfer(
                    payment.getId(),
                    sourceAccount,
                    destinationAccount,
                    command.amount()
            );

            // Persist domain changes
            accountRepository.save(result.updatedSourceAccount());
            accountRepository.save(result.updatedDestinationAccount());
            ledgerEntryRepository.save(result.debitEntry());
            ledgerEntryRepository.save(result.creditEntry());

            payment.markCompleted();
            payment = paymentRepository.save(payment);
            paymentEventPublisher.paymentCompleted(payment);

            idempotencyPort.complete(command.idempotencyKey(), serializePayment(payment));
            return payment;

        } catch (RuntimeException e) {
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Payment getPaymentById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    @Override
    @Transactional(readOnly = true)
    public Payment getPaymentByIdempotencyKey(String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new PaymentNotFoundException(idempotencyKey));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> getAllPayments() {
        return paymentRepository.findAllOrderByCreatedAtDesc();
    }

    @Override
    @Transactional
    public Payment refundPayment(UUID originalPaymentId, String refundIdempotencyKey) {
        Payment original = paymentRepository.findById(originalPaymentId)
                .orElseThrow(() -> new PaymentNotFoundException(originalPaymentId));

        if (!original.canBeRefunded()) {
            throw new IllegalStateException("Only COMPLETED payments can be refunded. Current status: " + original.getStatus());
        }

        ProcessPaymentCommand refundCommand = new ProcessPaymentCommand(
                refundIdempotencyKey,
                original.getDestinationAccountId(),
                original.getSourceAccountId(),
                original.getMoney()
        );

        Payment refundPayment = processPayment(refundCommand);

        original.markRefunded();
        paymentRepository.save(original);

        return refundPayment;
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(50L * (1L << attempt));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String serialize(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new IllegalStateException("Serialization error", e);
        }
    }

    private String serializePayment(Payment payment) {
        try {
            return objectMapper.writeValueAsString(payment);
        } catch (Exception e) {
            throw new IllegalStateException("Payment serialization error", e);
        }
    }

    private Payment deserializePayment(String json) {
        try {
            return objectMapper.readValue(json, Payment.class);
        } catch (Exception e) {
            throw new IllegalStateException("Payment deserialization error", e);
        }
    }
}
