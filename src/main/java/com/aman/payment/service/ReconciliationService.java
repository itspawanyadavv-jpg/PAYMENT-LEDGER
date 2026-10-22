package com.aman.payment.service;

import com.aman.payment.entity.Account;
import com.aman.payment.repository.AccountRepository;
import com.aman.payment.repository.LedgerEntryRepository;
import com.aman.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs periodically to catch corruption or drift that shouldn't happen
 * if the ledger invariants hold, but which a reconciliation job exists
 * to detect early rather than trust blindly:
 *
 *  1. Account.balance must equal the sum of that account's ledger
 *     entries (credits minus debits). A mismatch means either a bug
 *     wrote to balance without a matching ledger entry, or vice versa.
 *  2. Every payment's ledger entries must net to zero (debits ==
 *     credits) - the fundamental double-entry invariant.
 *
 * This never auto-corrects data; it only reports discrepancies for a
 * human/alerting pipeline to investigate, since silently "fixing" a
 * financial ledger is its own risk.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentRepository paymentRepository;

    public record Discrepancy(String type, String referenceId, String detail) {
    }

    @Scheduled(cron = "0 0 * * * *") // hourly
    public void runScheduledReconciliation() {
        List<Discrepancy> discrepancies = reconcileAll();
        if (!discrepancies.isEmpty()) {
            log.error("Reconciliation found {} discrepancies: {}", discrepancies.size(), discrepancies);
        } else {
            log.info("Reconciliation completed with no discrepancies");
        }
    }

    @Transactional(readOnly = true)
    public List<Discrepancy> reconcileAll() {
        List<Discrepancy> discrepancies = new ArrayList<>();
        discrepancies.addAll(reconcileAccountBalances());
        discrepancies.addAll(reconcilePaymentInvariants());
        return discrepancies;
    }

    private List<Discrepancy> reconcileAccountBalances() {
        List<Discrepancy> discrepancies = new ArrayList<>();
        for (Account account : accountRepository.findAll()) {
            if (account.isSystemAccount()) {
                continue; // System clearing float accounts represent external settlement
            }
            BigDecimal computed = ledgerEntryRepository.computeBalanceFromEntries(account.getId());
            if (computed.compareTo(account.getBalance()) != 0) {
                discrepancies.add(new Discrepancy(
                        "ACCOUNT_BALANCE_MISMATCH",
                        account.getId().toString(),
                        "stored=" + account.getBalance() + " computed=" + computed));
            }
        }
        return discrepancies;
    }

    private List<Discrepancy> reconcilePaymentInvariants() {
        List<Discrepancy> discrepancies = new ArrayList<>();
        paymentRepository.findByStatus(com.aman.payment.entity.Payment.Status.COMPLETED).forEach(payment -> {
            BigDecimal net = ledgerEntryRepository.sumNetAmountForPayment(payment.getId());
            if (net.compareTo(BigDecimal.ZERO) != 0) {
                discrepancies.add(new Discrepancy(
                        "PAYMENT_NOT_BALANCED",
                        payment.getId().toString(),
                        "net=" + net));
            }
        });
        return discrepancies;
    }
}
