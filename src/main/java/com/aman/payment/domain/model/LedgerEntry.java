package com.aman.payment.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable domain model representing a single entry in the double-entry ledger.
 * Financial ledgers are strictly append-only.
 */
public class LedgerEntry {

    private final UUID id;
    private final UUID paymentId;
    private final UUID accountId;
    private final EntryType entryType;
    private final Money amount;
    private final Money balanceAfter;
    private final Instant createdAt;

    public LedgerEntry(UUID id,
                       UUID paymentId,
                       UUID accountId,
                       EntryType entryType,
                       Money amount,
                       Money balanceAfter,
                       Instant createdAt) {
        this.id = Objects.requireNonNull(id, "Entry ID cannot be null");
        this.paymentId = Objects.requireNonNull(paymentId, "Payment ID cannot be null");
        this.accountId = Objects.requireNonNull(accountId, "Account ID cannot be null");
        this.entryType = Objects.requireNonNull(entryType, "Entry type cannot be null");
        this.amount = Objects.requireNonNull(amount, "Amount cannot be null");
        this.balanceAfter = Objects.requireNonNull(balanceAfter, "BalanceAfter cannot be null");
        this.createdAt = createdAt != null ? createdAt : Instant.now();

        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Ledger entry amount must be strictly positive");
        }
        if (!amount.getCurrencyCode().equals(balanceAfter.getCurrencyCode())) {
            throw new IllegalArgumentException("Entry amount currency must match balanceAfter currency");
        }
    }

    public static LedgerEntry create(UUID paymentId,
                                     UUID accountId,
                                     EntryType entryType,
                                     Money amount,
                                     Money balanceAfter) {
        return new LedgerEntry(UUID.randomUUID(), paymentId, accountId, entryType, amount, balanceAfter, Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public Money getAmount() {
        return amount;
    }

    public Money getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isDebit() {
        return entryType == EntryType.DEBIT;
    }

    public boolean isCredit() {
        return entryType == EntryType.CREDIT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LedgerEntry that = (LedgerEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
