package com.aman.payment.domain.exception;

public class InvalidAccountException extends DomainException {
    public InvalidAccountException(String message) {
        super(message);
    }
}
