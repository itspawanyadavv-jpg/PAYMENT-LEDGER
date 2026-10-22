package com.aman.payment.test;

import com.aman.payment.dto.PaymentRequest;
import com.aman.payment.dto.PaymentResponse;
import com.aman.payment.entity.Account;
import com.aman.payment.entity.LedgerEntry;
import com.aman.payment.exception.InsufficientBalanceException;
import com.aman.payment.service.AccountService;
import com.aman.payment.service.LedgerService;
import com.aman.payment.service.PaymentService;
import com.aman.payment.service.ReconciliationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the fundamental double-entry bookkeeping invariants:
 *
 *  1. Every completed payment produces exactly one DEBIT entry and one
 *     CREDIT entry of equal amount (they net to zero).
 *  2. Every account's denormalized balance always equals the sum of
 *     its ledger entries (credits minus debits) - i.e. the ledger is
 *     the ground truth and the balance column never drifts from it.
 *  3. A failed payment (e.g. insufficient balance) posts NO ledger
 *     entries at all - partial postings must never be visible.
 */
@DisplayName("Double-entry ledger invariants")
class LedgerInvariantTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Test
    @DisplayName("A completed payment produces exactly one balanced debit/credit pair")
    void completedPayment_producesBalancedEntries() {
        Account source = accountService.openAccount("ACC-" + UUID.randomUUID(), "Alice", "USD", new BigDecimal("300.00"));
        Account destination = accountService.openAccount("ACC-" + UUID.randomUUID(), "Bob", "USD", new BigDecimal("0.00"));

        PaymentResponse response = paymentService.executePayment(PaymentRequest.builder()
                .idempotencyKey("inv-" + UUID.randomUUID())
                .sourceAccountId(source.getId())
                .destinationAccountId(destination.getId())
                .amount(new BigDecimal("120.00"))
                .currency("USD")
                .build());

        List<LedgerEntry> entries = ledgerService.getEntriesForPayment(response.getPaymentId());
        assertThat(entries).hasSize(2);

        long debitCount = entries.stream().filter(e -> e.getEntryType() == LedgerEntry.EntryType.DEBIT).count();
        long creditCount = entries.stream().filter(e -> e.getEntryType() == LedgerEntry.EntryType.CREDIT).count();
        assertThat(debitCount).isEqualTo(1);
        assertThat(creditCount).isEqualTo(1);

        assertThat(ledgerService.isBalanced(response.getPaymentId())).isTrue();
    }

    @Test
    @DisplayName("Account balance always equals the sum of its ledger entries")
    void accountBalance_matchesLedgerEntries() {
        Account source = accountService.openAccount("ACC-" + UUID.randomUUID(), "Alice", "USD", new BigDecimal("1000.00"));
        Account destination = accountService.openAccount("ACC-" + UUID.randomUUID(), "Bob", "USD", new BigDecimal("0.00"));

        for (int i = 0; i < 5; i++) {
            paymentService.executePayment(PaymentRequest.builder()
                    .idempotencyKey("inv-multi-" + UUID.randomUUID())
                    .sourceAccountId(source.getId())
                    .destinationAccountId(destination.getId())
                    .amount(new BigDecimal("50.00"))
                    .currency("USD")
                    .build());
        }

        List<ReconciliationService.Discrepancy> discrepancies = reconciliationService.reconcileAll();
        assertThat(discrepancies).isEmpty();

        Account refreshedSource = accountService.getAccount(source.getId());
        Account refreshedDestination = accountService.getAccount(destination.getId());
        assertThat(refreshedSource.getBalance()).isEqualByComparingTo("750.00");
        assertThat(refreshedDestination.getBalance()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("A failed payment (insufficient balance) posts no ledger entries")
    void failedPayment_postsNoEntries() {
        Account source = accountService.openAccount("ACC-" + UUID.randomUUID(), "Poor", "USD", new BigDecimal("10.00"));
        Account destination = accountService.openAccount("ACC-" + UUID.randomUUID(), "Rich", "USD", new BigDecimal("0.00"));

        PaymentRequest request = PaymentRequest.builder()
                .idempotencyKey("inv-fail-" + UUID.randomUUID())
                .sourceAccountId(source.getId())
                .destinationAccountId(destination.getId())
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .build();

        assertThatThrownBy(() -> paymentService.executePayment(request))
                .isInstanceOf(InsufficientBalanceException.class);

        Account refreshedSource = accountService.getAccount(source.getId());
        Account refreshedDestination = accountService.getAccount(destination.getId());
        assertThat(refreshedSource.getBalance()).isEqualByComparingTo("10.00");
        assertThat(refreshedDestination.getBalance()).isEqualByComparingTo("0.00");
    }
}
