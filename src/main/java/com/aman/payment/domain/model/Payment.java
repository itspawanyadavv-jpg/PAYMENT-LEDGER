package com.aman.payment.domain.model;

import com.aman.payment.domain.exception.InvalidPaymentStateException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pure domain entity representing a financial Payment.
 * Fully encapsulated without persistence/framework annotations.
 */
public class Payment {

    private final UUID id;
    private final String idempotencyKey;
    private final UUID sourceAccountId;
    private final UUID destinationAccountId;
    private final Money money;
    private PaymentStatus status;
    private String failureReason;
    private final Instant createdAt;
    private Instant updatedAt;

    public Payment(UUID id,
                   String idempotencyKey,
                   UUID sourceAccountId,
                   UUID destinationAccountId,
                   Money money,
                   PaymentStatus status,
                   String failureReason,
                   Instant createdAt,
                   Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "Payment ID cannot be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "Idempotency key cannot be null");
        this.sourceAccountId = Objects.requireNonNull(sourceAccountId, "Source account ID cannot be null");
        this.destinationAccountId = Objects.requireNonNull(destinationAccountId, "Destination account ID cannot be null");
        this.money = Objects.requireNonNull(money, "Money cannot be null");
        this.status = status != null ? status : PaymentStatus.PENDING;
        this.failureReason = failureReason;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;

        validateInvariants();
    }

    public static Payment create(String idempotencyKey,
                                 UUID sourceAccountId,
                                 UUID destinationAccountId,
                                 Money money) {
        return create(UUID.randomUUID(), idempotencyKey, sourceAccountId, destinationAccountId, money);
    }

    public static Payment create(UUID id,
                                 String idempotencyKey,
                                 UUID sourceAccountId,
                                 UUID destinationAccountId,
                                 Money money) {
        Instant now = Instant.now();
        return new Payment(id, idempotencyKey, sourceAccountId, destinationAccountId, money,
                PaymentStatus.PENDING, null, now, now);
    }

    private void validateInvariants() {
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("Source and destination accounts must be distinct");
        }
        if (!money.isPositive()) {
            throw new IllegalArgumentException("Payment amount must be strictly positive");
        }
    }

    public void markCompleted() {
        if (this.status != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException(
                    String.format("Cannot complete payment %s in status %s", id, status));
        }
        this.status = PaymentStatus.COMPLETED;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        if (this.status != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException(
                    String.format("Cannot fail payment %s in status %s", id, status));
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markRefunded() {
        if (this.status != PaymentStatus.COMPLETED) {
            throw new InvalidPaymentStateException(
                    String.format("Only COMPLETED payments can be refunded. Current status: %s", status));
        }
        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = Instant.now();
    }

    public boolean canBeRefunded() {
        return this.status == PaymentStatus.COMPLETED;
    }

    public boolean isCompleted() {
        return this.status == PaymentStatus.COMPLETED;
    }

    public boolean isPending() {
        return this.status == PaymentStatus.PENDING;
    }

    public boolean isFailed() {
        return this.status == PaymentStatus.FAILED;
    }

    public boolean isRefunded() {
        return this.status == PaymentStatus.REFUNDED;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public Money getMoney() {
        return money;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payment payment = (Payment) o;
        return Objects.equals(id, payment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
