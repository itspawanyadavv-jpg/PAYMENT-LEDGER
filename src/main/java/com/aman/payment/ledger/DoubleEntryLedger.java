package com.aman.payment.ledger;

import com.aman.payment.entity.LedgerEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Core double-entry primitive: every money movement is exactly one
 * debit and one credit of equal amount, applied atomically.
 *
 * Deadlock avoidance: when two concurrent payments touch the same pair
 * of accounts in opposite directions (A->B and B->A), locking them in
 * whatever order the debit/credit roles happen to imply can deadlock.
 * We always acquire row locks in a fixed, account-id-based order
 * (smallest UUID first) regardless of debit/credit role, which makes
 * lock acquisition order consistent across all transactions and
 * eliminates the classic "dining philosophers" deadlock.
 */
@Component
@RequiredArgsConstructor
public class DoubleEntryLedger {

    private final DebitProcessor debitProcessor;
    private final CreditProcessor creditProcessor;

    public Result post(UUID paymentId, UUID debitAccountId, UUID creditAccountId,
                        BigDecimal amount, String currency) {

        boolean debitFirst = debitAccountId.compareTo(creditAccountId) <= 0;

        LedgerEntry debitEntry;
        LedgerEntry creditEntry;

        if (debitFirst) {
            debitEntry = debitProcessor.process(paymentId, debitAccountId, amount, currency);
            creditEntry = creditProcessor.process(paymentId, creditAccountId, amount, currency);
        } else {
            creditEntry = creditProcessor.process(paymentId, creditAccountId, amount, currency);
            debitEntry = debitProcessor.process(paymentId, debitAccountId, amount, currency);
        }

        return new Result(debitEntry, creditEntry);
    }

    public record Result(LedgerEntry debitEntry, LedgerEntry creditEntry) {
    }
}
