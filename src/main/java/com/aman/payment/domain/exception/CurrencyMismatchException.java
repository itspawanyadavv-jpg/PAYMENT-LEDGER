package com.aman.payment.domain.exception;

public class CurrencyMismatchException extends DomainException {
    private final String expectedCurrency;
    private final String actualCurrency;

    public CurrencyMismatchException(String expectedCurrency, String actualCurrency) {
        super(String.format("Currency mismatch: expected %s, but got %s", expectedCurrency, actualCurrency));
        this.expectedCurrency = expectedCurrency;
        this.actualCurrency = actualCurrency;
    }

    public String getExpectedCurrency() {
        return expectedCurrency;
    }

    public String getActualCurrency() {
        return actualCurrency;
    }
}
