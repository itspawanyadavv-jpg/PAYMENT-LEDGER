package com.aman.payment.application.service;

import com.aman.payment.application.port.in.QueryLedgerUseCase;
import com.aman.payment.application.port.out.AccountRepositoryPort;
import com.aman.payment.application.port.out.LedgerEntryRepositoryPort;
import com.aman.payment.application.port.out.PaymentRepositoryPort;
import com.aman.payment.domain.model.Account;
import com.aman.payment.domain.model.LedgerEntry;
import com.aman.payment.domain.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerApplicationService implements QueryLedgerUseCase {

    private final LedgerEntryRepositoryPort ledgerEntryRepository;
    private final AccountRepositoryPort accountRepository;
    private final PaymentRepositoryPort paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntry> getAccountHistory(UUID accountId) {
        return ledgerEntryRepository.findByAccountId(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntry> getEntriesForPayment(UUID paymentId) {
        return ledgerEntryRepository.findByPaymentId(paymentId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBalanced(UUID paymentId) {
        return ledgerEntryRepository.sumNetAmountForPayment(paymentId).compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntry> getRecentEntries(int limit) {
        return ledgerEntryRepository.findRecent(Math.min(limit, 200));
    }

    @Scheduled(cron = "0 0 * * * *") // hourly
    public void runScheduledReconciliation() {
        List<Discrepancy> discrepancies = reconcileAll();
        if (!discrepancies.isEmpty()) {
            log.error("Scheduled reconciliation found {} discrepancies: {}", discrepancies.size(), discrepancies);
        } else {
            log.info("Scheduled reconciliation completed with no discrepancies");
        }
    }

    @Override
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
                continue;
            }
            BigDecimal computed = ledgerEntryRepository.computeBalanceFromEntries(account.getId());
            if (computed.compareTo(account.getBalance().getAmount()) != 0) {
                discrepancies.add(new Discrepancy(
                        "ACCOUNT_BALANCE_MISMATCH",
                        account.getId().toString(),
                        "stored=" + account.getBalance().getAmount() + " computed=" + computed));
            }
        }
        return discrepancies;
    }

    private List<Discrepancy> reconcilePaymentInvariants() {
        List<Discrepancy> discrepancies = new ArrayList<>();
        paymentRepository.findByStatus(PaymentStatus.COMPLETED).forEach(payment -> {
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
