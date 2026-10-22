package com.aman.payment.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(UUID accountId, BigDecimal available, BigDecimal requested) {
        super(String.format(
                "Account %s has insufficient balance: available=%s, requested=%s",
                accountId, available, requested));
    }
}
