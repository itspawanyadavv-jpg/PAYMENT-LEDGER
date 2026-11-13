package com.aman.payment.domain.exception;

import java.util.UUID;

public class PaymentNotFoundException extends DomainException {
    public PaymentNotFoundException(UUID paymentId) {
        super("Payment not found: " + paymentId);
    }

    public PaymentNotFoundException(String idempotencyKey) {
        super("Payment not found for idempotency key: " + idempotencyKey);
    }
}
