package com.aman.payment.ledger;

import com.aman.payment.entity.Account;
import com.aman.payment.entity.LedgerEntry;
import com.aman.payment.repository.AccountRepository;
import com.aman.payment.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Applies the credit leg of a payment: locks the destination account
 * row, increments its balance, and appends the matching ledger entry.
 * Credits never fail on a balance check (there's no such thing as
 * "insufficient balance" to receive money) but currency mismatches are
 * still rejected.
 */
@Component
@RequiredArgsConstructor
public class CreditProcessor {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerEntry process(UUID paymentId, UUID accountId, BigDecimal amount, String currency) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Account not found: " + accountId));

        if (!account.isActive()) {
            throw new IllegalStateException("Destination account " + account.getAccountNumber() + " is " + account.getStatus());
        }

        if (!account.getCurrency().equals(currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: account is " + account.getCurrency() + ", payment is " + currency);
        }

        account.credit(amount);
        accountRepository.save(account);

        LedgerEntry entry = LedgerEntry.builder()
                .paymentId(paymentId)
                .accountId(accountId)
                .entryType(LedgerEntry.EntryType.CREDIT)
                .amount(amount)
                .currency(currency)
                .balanceAfter(account.getBalance())
                .build();

        return ledgerEntryRepository.save(entry);
    }
}
