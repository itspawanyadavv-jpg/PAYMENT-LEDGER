package com.aman.payment.service;

import com.aman.payment.entity.LedgerEntry;
import com.aman.payment.ledger.DoubleEntryLedger;
import com.aman.payment.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Application-facing ledger service: exposes posting (delegated to
 * DoubleEntryLedger, which must run in the caller's existing
 * transaction) plus read queries for statements/history.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final DoubleEntryLedger doubleEntryLedger;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public DoubleEntryLedger.Result post(UUID paymentId, UUID debitAccountId, UUID creditAccountId,
                                          BigDecimal amount, String currency) {
        return doubleEntryLedger.post(paymentId, debitAccountId, creditAccountId, amount, currency);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getAccountHistory(UUID accountId) {
        return ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getEntriesForPayment(UUID paymentId) {
        return ledgerEntryRepository.findByPaymentId(paymentId);
    }

    /**
     * Returns true iff this payment's debit and credit legs net to
     * zero - the fundamental double-entry invariant.
     */
    @Transactional(readOnly = true)
    public boolean isBalanced(UUID paymentId) {
        return ledgerEntryRepository.sumNetAmountForPayment(paymentId).compareTo(BigDecimal.ZERO) == 0;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getRecentEntries(int limit) {
        return ledgerEntryRepository.findAll(org.springframework.data.domain.PageRequest.of(
                0, Math.min(limit, 200), org.springframework.data.domain.Sort.by("createdAt").descending())).getContent();
    }
}
