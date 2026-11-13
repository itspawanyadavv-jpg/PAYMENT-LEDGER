package com.aman.payment.domain.exception;

import com.aman.payment.domain.model.Money;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientBalanceException extends DomainException {
    private final UUID accountId;
    private final Money currentBalance;
    private final Money debitAmount;

    public InsufficientBalanceException(UUID accountId, Money currentBalance, Money debitAmount) {
        super(String.format("Account %s has insufficient balance: current=%s, required=%s",
                accountId, currentBalance, debitAmount));
        this.accountId = accountId;
        this.currentBalance = currentBalance;
        this.debitAmount = debitAmount;
    }

    public InsufficientBalanceException(UUID accountId, BigDecimal currentBalance, BigDecimal debitAmount) {
        super(String.format("Account %s has insufficient balance: current=%s, required=%s",
                accountId, currentBalance, debitAmount));
        this.accountId = accountId;
        this.currentBalance = null;
        this.debitAmount = null;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Money getCurrentBalance() {
        return currentBalance;
    }

    public Money getDebitAmount() {
        return debitAmount;
    }
}
