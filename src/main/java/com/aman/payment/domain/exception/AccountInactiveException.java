package com.aman.payment.domain.exception;

public class AccountInactiveException extends DomainException {
    public AccountInactiveException(String message) {
        super(message);
    }
}
