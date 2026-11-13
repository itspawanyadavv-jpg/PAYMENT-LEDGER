package com.aman.payment.test;

import com.aman.payment.domain.exception.CurrencyMismatchException;
import com.aman.payment.domain.exception.InsufficientBalanceException;
import com.aman.payment.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DoubleEntryLedger Domain Specification")
class DoubleEntryLedgerTest {

    private final DoubleEntryLedger ledger = new DoubleEntryLedger();

    @Test
    @DisplayName("Should post balanced debit and credit entries")
    void shouldPostBalancedEntries() {
        Account source = Account.create("ACC-001", "Alice", "USD", Money.of("500.00", "USD"));
        Account dest = Account.create("ACC-002", "Bob", "USD", Money.of("100.00", "USD"));
        UUID paymentId = UUID.randomUUID();

        DoubleEntryLedger.PostingResult result = ledger.postTransfer(
                paymentId, source, dest, Money.of("200.00", "USD"));

        assertThat(result.debitEntry().getEntryType()).isEqualTo(EntryType.DEBIT);
        assertThat(result.debitEntry().getAmount()).isEqualTo(Money.of("200.00", "USD"));
        assertThat(result.creditEntry().getEntryType()).isEqualTo(EntryType.CREDIT);
        assertThat(result.creditEntry().getAmount()).isEqualTo(Money.of("200.00", "USD"));

        // Debit == Credit invariant
        assertThat(result.debitEntry().getAmount()).isEqualTo(result.creditEntry().getAmount());

        // Balances updated
        assertThat(source.getBalance()).isEqualTo(Money.of("300.00", "USD"));
        assertThat(dest.getBalance()).isEqualTo(Money.of("300.00", "USD"));
    }

    @Test
    @DisplayName("Should fail when source has insufficient funds")
    void shouldFailInsufficientBalance() {
        Account source = Account.create("ACC-001", "Alice", "USD", Money.of("50.00", "USD"));
        Account dest = Account.create("ACC-002", "Bob", "USD", Money.of("100.00", "USD"));
        UUID paymentId = UUID.randomUUID();

        assertThatThrownBy(() -> ledger.postTransfer(paymentId, source, dest, Money.of("100.00", "USD")))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    @DisplayName("Should fail when currencies do not match")
    void shouldFailCurrencyMismatch() {
        Account source = Account.create("ACC-001", "Alice", "USD", Money.of("500.00", "USD"));
        Account dest = Account.create("ACC-002", "Bob", "EUR", Money.of("100.00", "EUR"));
        UUID paymentId = UUID.randomUUID();

        assertThatThrownBy(() -> ledger.postTransfer(paymentId, source, dest, Money.of("50.00", "USD")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("Should fail when source and destination are the same account")
    void shouldFailSameAccount() {
        Account source = Account.create("ACC-001", "Alice", "USD", Money.of("500.00", "USD"));
        UUID paymentId = UUID.randomUUID();

        assertThatThrownBy(() -> ledger.postTransfer(paymentId, source, source, Money.of("50.00", "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
