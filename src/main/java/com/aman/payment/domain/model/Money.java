package com.aman.payment.domain.model;

import com.aman.payment.domain.exception.CurrencyMismatchException;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable Value Object representing monetary amounts.
 * Encapsulates BigDecimal amount and Currency, strictly enforcing:
 * - Scale of 4 decimal places with standard banker's rounding (RoundingMode.HALF_EVEN)
 * - ISO-4217 Currency representation
 * - Currency invariance across arithmetic operations
 */
public final class Money implements Serializable, Comparable<Money> {

    public static final int DEFAULT_SCALE = 4;
    public static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_EVEN;

    private final BigDecimal amount;
    private final Currency currency;

    private Money(BigDecimal amount, Currency currency) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(currency, "Currency cannot be null");
        this.amount = amount.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING_MODE);
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        Objects.requireNonNull(currencyCode, "Currency code cannot be null");
        return new Money(amount, Currency.getInstance(currencyCode.trim().toUpperCase()));
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amountStr, String currencyCode) {
        Objects.requireNonNull(amountStr, "Amount string cannot be null");
        return of(new BigDecimal(amountStr.trim()), currencyCode);
    }

    public static Money of(double amount, String currencyCode) {
        return of(BigDecimal.valueOf(amount), currencyCode);
    }

    public static Money zero(String currencyCode) {
        return of(BigDecimal.ZERO, currencyCode);
    }

    public static Money zero(Currency currency) {
        return of(BigDecimal.ZERO, currency);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getCurrencyCode() {
        return currency.getCurrencyCode();
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money multiply(long factor) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(factor)), this.currency);
    }

    public Money multiply(BigDecimal factor) {
        Objects.requireNonNull(factor, "Factor cannot be null");
        return new Money(this.amount.multiply(factor), this.currency);
    }

    public Money negate() {
        return new Money(this.amount.negate(), this.currency);
    }

    public Money abs() {
        return new Money(this.amount.abs(), this.currency);
    }

    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegative() {
        return this.amount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }

    public boolean isLessThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) < 0;
    }

    public boolean isLessThanOrEqualTo(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) <= 0;
    }

    public boolean sameCurrencyAs(Money other) {
        return other != null && this.currency.equals(other.currency);
    }

    public void assertSameCurrency(Money other) {
        Objects.requireNonNull(other, "Money operand cannot be null");
        if (!this.currency.equals(other.currency)) {
            throw new CurrencyMismatchException(this.getCurrencyCode(), other.getCurrencyCode());
        }
    }

    @Override
    public int compareTo(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return this.amount.compareTo(money.amount) == 0 && this.currency.equals(money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
