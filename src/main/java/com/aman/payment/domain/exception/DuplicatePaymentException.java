package com.aman.payment.domain.exception;

public class DuplicatePaymentException extends DomainException {
    public DuplicatePaymentException(String idempotencyKey) {
        super("A payment with idempotency key '" + idempotencyKey + "' already exists with different payload or conflicting state");
    }
}
