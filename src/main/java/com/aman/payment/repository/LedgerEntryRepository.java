package com.aman.payment.repository;

import com.aman.payment.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<LedgerEntry> findByPaymentId(UUID paymentId);

    /**
     * Sums all DEBIT entries minus all CREDIT entries for a payment.
     * Used by LedgerInvariantTest / ReconciliationService to assert the
     * fundamental double-entry invariant: every payment's debits and
     * credits must net to zero.
     */
    @Query("""
           select coalesce(sum(case when e.entryType = 'DEBIT' then e.amount else -e.amount end), 0)
           from LedgerEntry e where e.paymentId = :paymentId
           """)
    BigDecimal sumNetAmountForPayment(@Param("paymentId") UUID paymentId);

    /**
     * Recomputes an account's balance directly from its ledger entries,
     * independent of the denormalized Account.balance column. Used for
     * reconciliation to detect drift.
     */
    @Query("""
           select coalesce(sum(case when e.entryType = 'CREDIT' then e.amount else -e.amount end), 0)
           from LedgerEntry e where e.accountId = :accountId
           """)
    BigDecimal computeBalanceFromEntries(@Param("accountId") UUID accountId);
}
