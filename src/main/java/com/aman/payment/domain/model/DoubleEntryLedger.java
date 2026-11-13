package com.aman.payment.domain.model;

import com.aman.payment.domain.exception.CurrencyMismatchException;
import com.aman.payment.domain.exception.LedgerImbalanceException;

import java.util.Objects;
import java.util.UUID;

/**
 * Rich domain aggregate / service implementing double-entry bookkeeping rules.
 * Natively enforces the fundamental invariant: Total Debits == Total Credits.
 * Any money transfer produces strictly balanced pairs of append-only ledger entries.
 */
public class DoubleEntryLedger {

    public record PostingResult(
            LedgerEntry debitEntry,
            LedgerEntry creditEntry,
            Account updatedSourceAccount,
            Account updatedDestinationAccount
    ) {
        public PostingResult {
            Objects.requireNonNull(debitEntry, "debitEntry cannot be null");
            Objects.requireNonNull(creditEntry, "creditEntry cannot be null");
            Objects.requireNonNull(updatedSourceAccount, "updatedSourceAccount cannot be null");
            Objects.requireNonNull(updatedDestinationAccount, "updatedDestinationAccount cannot be null");

            if (!debitEntry.getAmount().equals(creditEntry.getAmount())) {
                throw new LedgerImbalanceException(String.format(
                        "Double-entry bookkeeping invariant violated! Debit=%s, Credit=%s",
                        debitEntry.getAmount(), creditEntry.getAmount()));
            }
        }
    }

    /**
     * Executes a double-entry posting between two accounts.
     * Enforces currency matching, active account states, balance sufficiency,
     * and guarantees debits strictly equal credits.
     */
    public PostingResult postTransfer(UUID paymentId,
                                      Account sourceAccount,
                                      Account destinationAccount,
                                      Money amount) {
        Objects.requireNonNull(paymentId, "paymentId cannot be null");
        Objects.requireNonNull(sourceAccount, "sourceAccount cannot be null");
        Objects.requireNonNull(destinationAccount, "destinationAccount cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");

        if (sourceAccount.getId().equals(destinationAccount.getId())) {
            throw new IllegalArgumentException("Source and destination accounts must be distinct: " + sourceAccount.getId());
        }

        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Transfer amount must be strictly positive: " + amount);
        }

        // Account lifecycle validations
        sourceAccount.assertActive();
        destinationAccount.assertActive();

        // Currency validation across all legs
        if (!sourceAccount.getCurrency().equalsIgnoreCase(amount.getCurrencyCode())) {
            throw new CurrencyMismatchException(sourceAccount.getCurrency(), amount.getCurrencyCode());
        }
        if (!destinationAccount.getCurrency().equalsIgnoreCase(amount.getCurrencyCode())) {
            throw new CurrencyMismatchException(destinationAccount.getCurrency(), amount.getCurrencyCode());
        }

        // Apply debit and credit legs to rich domain entities
        sourceAccount.debit(amount);
        destinationAccount.credit(amount);

        // Generate immutable ledger entry records
        LedgerEntry debitEntry = LedgerEntry.create(
                paymentId,
                sourceAccount.getId(),
                EntryType.DEBIT,
                amount,
                sourceAccount.getBalance()
        );

        LedgerEntry creditEntry = LedgerEntry.create(
                paymentId,
                destinationAccount.getId(),
                EntryType.CREDIT,
                amount,
                destinationAccount.getBalance()
        );

        // Invariant check is performed in PostingResult compact constructor
        return new PostingResult(debitEntry, creditEntry, sourceAccount, destinationAccount);
    }
}
