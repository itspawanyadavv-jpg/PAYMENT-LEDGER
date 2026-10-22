package com.aman.payment.exception;

/**
 * Thrown when an idempotency key is reused with a different request
 * payload than the one originally associated with it - i.e. not a
 * legitimate retry but a key collision / client bug.
 */
public class DuplicatePaymentException extends RuntimeException {

    public DuplicatePaymentException(String idempotencyKey) {
        super("Idempotency key already used with a different payload: " + idempotencyKey);
    }
}
