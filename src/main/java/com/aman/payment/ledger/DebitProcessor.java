package com.aman.payment.ledger;

import com.aman.payment.entity.Account;
import com.aman.payment.entity.LedgerEntry;
import com.aman.payment.exception.InsufficientBalanceException;
import com.aman.payment.repository.AccountRepository;
import com.aman.payment.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Applies the debit leg of a payment: locks the source account row,
 * validates sufficient balance, decrements it, and appends an
 * immutable ledger entry recording the balance snapshot after the
 * debit. Must run inside the same transaction as CreditProcessor's
 * corresponding credit so the two legs commit atomically.
 */
@Component
@RequiredArgsConstructor
public class DebitProcessor {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerEntry process(UUID paymentId, UUID accountId, BigDecimal amount, String currency) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Account not found: " + accountId));

        if (!account.isActive()) {
            throw new IllegalStateException("Source account " + account.getAccountNumber() + " is " + account.getStatus());
        }

        if (!account.getCurrency().equals(currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: account is " + account.getCurrency() + ", payment is " + currency);
        }

        if (!account.isSystemAccount() && !account.hasSufficientBalance(amount)) {
            throw new InsufficientBalanceException(accountId, account.getBalance(), amount);
        }

        account.debit(amount);
        accountRepository.save(account);

        LedgerEntry entry = LedgerEntry.builder()
                .paymentId(paymentId)
                .accountId(accountId)
                .entryType(LedgerEntry.EntryType.DEBIT)
                .amount(amount)
                .currency(currency)
                .balanceAfter(account.getBalance())
                .build();

        return ledgerEntryRepository.save(entry);
    }
}
