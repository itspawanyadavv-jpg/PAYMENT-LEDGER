package com.aman.payment.test;

import com.aman.payment.application.port.in.ManageAccountUseCase;
import com.aman.payment.application.port.in.ProcessPaymentUseCase;
import com.aman.payment.application.port.in.QueryLedgerUseCase;
import com.aman.payment.domain.exception.InsufficientBalanceException;
import com.aman.payment.domain.model.Account;
import com.aman.payment.domain.model.EntryType;
import com.aman.payment.domain.model.LedgerEntry;
import com.aman.payment.domain.model.Money;
import com.aman.payment.domain.model.Payment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Double-entry ledger invariants")
class LedgerInvariantTest extends AbstractIntegrationTest {

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private ManageAccountUseCase manageAccountUseCase;

    @Autowired
    private QueryLedgerUseCase queryLedgerUseCase;

    @Test
    @DisplayName("A completed payment produces exactly one balanced debit/credit pair")
    void completedPayment_producesBalancedEntries() {
        Account source = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Alice", "USD", Money.of("300.00", "USD"));
        Account destination = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Bob", "USD", Money.of("0.00", "USD"));

        Payment payment = processPaymentUseCase.processPayment(
                new ProcessPaymentUseCase.ProcessPaymentCommand(
                        "inv-" + UUID.randomUUID(),
                        source.getId(),
                        destination.getId(),
                        Money.of("120.00", "USD")
                ));

        List<LedgerEntry> entries = queryLedgerUseCase.getEntriesForPayment(payment.getId());
        assertThat(entries).hasSize(2);

        long debitCount = entries.stream().filter(e -> e.getEntryType() == EntryType.DEBIT).count();
        long creditCount = entries.stream().filter(e -> e.getEntryType() == EntryType.CREDIT).count();
        assertThat(debitCount).isEqualTo(1);
        assertThat(creditCount).isEqualTo(1);

        assertThat(queryLedgerUseCase.isBalanced(payment.getId())).isTrue();
    }

    @Test
    @DisplayName("Account balance always equals the sum of its ledger entries")
    void accountBalance_matchesLedgerEntries() {
        Account source = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Alice", "USD", Money.of("1000.00", "USD"));
        Account destination = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Bob", "USD", Money.of("0.00", "USD"));

        for (int i = 0; i < 5; i++) {
            processPaymentUseCase.processPayment(
                    new ProcessPaymentUseCase.ProcessPaymentCommand(
                            "inv-multi-" + UUID.randomUUID(),
                            source.getId(),
                            destination.getId(),
                            Money.of("50.00", "USD")
                    ));
        }

        List<QueryLedgerUseCase.Discrepancy> discrepancies = queryLedgerUseCase.reconcileAll();
        assertThat(discrepancies).isEmpty();

        Account refreshedSource = manageAccountUseCase.getAccountById(source.getId());
        Account refreshedDestination = manageAccountUseCase.getAccountById(destination.getId());
        assertThat(refreshedSource.getBalance().getAmount()).isEqualByComparingTo(new BigDecimal("750.00"));
        assertThat(refreshedDestination.getBalance().getAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    @DisplayName("A failed payment (insufficient balance) posts no ledger entries")
    void failedPayment_postsNoEntries() {
        Account source = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Poor", "USD", Money.of("10.00", "USD"));
        Account destination = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Rich", "USD", Money.of("0.00", "USD"));

        ProcessPaymentUseCase.ProcessPaymentCommand command = new ProcessPaymentUseCase.ProcessPaymentCommand(
                "inv-fail-" + UUID.randomUUID(),
                source.getId(),
                destination.getId(),
                Money.of("500.00", "USD")
        );

        assertThatThrownBy(() -> processPaymentUseCase.processPayment(command))
                .isInstanceOf(InsufficientBalanceException.class);

        Account refreshedSource = manageAccountUseCase.getAccountById(source.getId());
        Account refreshedDestination = manageAccountUseCase.getAccountById(destination.getId());
        assertThat(refreshedSource.getBalance().getAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(refreshedDestination.getBalance().getAmount()).isEqualByComparingTo(new BigDecimal("0.00"));
    }
}
