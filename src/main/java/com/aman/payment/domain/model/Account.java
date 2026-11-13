package com.aman.payment.domain.model;

import com.aman.payment.domain.exception.AccountInactiveException;
import com.aman.payment.domain.exception.CurrencyMismatchException;
import com.aman.payment.domain.exception.InsufficientBalanceException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Rich domain entity representing a financial Account.
 * Contains core business rules regarding balance mutations and status transitions.
 */
public class Account {

    private final UUID id;
    private final String accountNumber;
    private final String ownerName;
    private final String currency;
    private Money balance;
    private AccountStatus status;
    private final boolean systemAccount;
    private Long version;
    private final Instant createdAt;
    private Instant updatedAt;

    public Account(UUID id,
                   String accountNumber,
                   String ownerName,
                   String currency,
                   Money balance,
                   AccountStatus status,
                   boolean systemAccount,
                   Long version,
                   Instant createdAt,
                   Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "Account ID cannot be null");
        this.accountNumber = Objects.requireNonNull(accountNumber, "Account number cannot be null");
        this.ownerName = Objects.requireNonNull(ownerName, "Owner name cannot be null");
        this.currency = Objects.requireNonNull(currency, "Currency cannot be null").toUpperCase();
        this.balance = balance != null ? balance : Money.zero(this.currency);
        this.status = status != null ? status : AccountStatus.ACTIVE;
        this.systemAccount = systemAccount;
        this.version = version != null ? version : 0L;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;

        if (!this.balance.getCurrencyCode().equals(this.currency)) {
            throw new CurrencyMismatchException(this.currency, this.balance.getCurrencyCode());
        }
    }

    public static Account create(String accountNumber, String ownerName, String currency, Money initialBalance) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Money bal = initialBalance != null ? initialBalance : Money.zero(currency);
        return new Account(id, accountNumber, ownerName, currency, bal, AccountStatus.ACTIVE, false, 0L, now, now);
    }

    public static Account createSystemAccount(String currency, Money initialFloat) {
        String accountNumber = "SYSTEM_RESERVE_" + currency.toUpperCase();
        String ownerName = "Central Reserve (" + currency.toUpperCase() + ")";
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return new Account(id, accountNumber, ownerName, currency.toUpperCase(), initialFloat, AccountStatus.ACTIVE, true, 0L, now, now);
    }

    public boolean isActive() {
        return this.status == AccountStatus.ACTIVE;
    }

    public void assertActive() {
        if (!isActive()) {
            throw new AccountInactiveException(
                    String.format("Account %s (%s) is not active. Current status: %s", accountNumber, id, status));
        }
    }

    public boolean hasSufficientBalance(Money amount) {
        validateCurrency(amount);
        return this.systemAccount || this.balance.isGreaterThanOrEqualTo(amount);
    }

    public void debit(Money amount) {
        assertActive();
        validateCurrency(amount);

        if (!systemAccount && !this.balance.isGreaterThanOrEqualTo(amount)) {
            throw new InsufficientBalanceException(this.id, this.balance, amount);
        }

        this.balance = this.balance.subtract(amount);
        this.updatedAt = Instant.now();
    }

    public void credit(Money amount) {
        assertActive();
        validateCurrency(amount);

        this.balance = this.balance.add(amount);
        this.updatedAt = Instant.now();
    }

    public void freeze() {
        if (this.status == AccountStatus.CLOSED) {
            throw new IllegalStateException("Cannot freeze a closed account: " + accountNumber);
        }
        this.status = AccountStatus.FROZEN;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        if (this.status == AccountStatus.CLOSED) {
            throw new IllegalStateException("Cannot activate a closed account: " + accountNumber);
        }
        this.status = AccountStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void close() {
        if (!this.balance.isZero() && !this.systemAccount) {
            throw new IllegalStateException("Cannot close account " + accountNumber + " with non-zero balance: " + balance);
        }
        this.status = AccountStatus.CLOSED;
        this.updatedAt = Instant.now();
    }

    public void updateStatus(AccountStatus newStatus) {
        switch (newStatus) {
            case ACTIVE -> activate();
            case FROZEN -> freeze();
            case CLOSED -> close();
        }
    }

    private void validateCurrency(Money amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (!this.currency.equalsIgnoreCase(amount.getCurrencyCode())) {
            throw new CurrencyMismatchException(this.currency, amount.getCurrencyCode());
        }
    }

    public UUID getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getCurrency() {
        return currency;
    }

    public Money getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public boolean isSystemAccount() {
        return systemAccount;
    }

    public Long getVersion() {
        return version;
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
        Account account = (Account) o;
        return Objects.equals(id, account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
