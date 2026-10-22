package com.aman.payment.service;

import com.aman.payment.entity.Account;
import com.aman.payment.repository.AccountRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PaymentService paymentService;

    public AccountService(AccountRepository accountRepository, @Lazy PaymentService paymentService) {
        this.accountRepository = accountRepository;
        this.paymentService = paymentService;
    }

    @Transactional
    public Account openAccount(String accountNumber, String ownerName, String currency, BigDecimal openingBalance) {
        accountRepository.findByAccountNumber(accountNumber).ifPresent(a -> {
            throw new IllegalArgumentException("Account number already exists: " + accountNumber);
        });

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .ownerName(ownerName)
                .currency(currency.toUpperCase())
                .balance(BigDecimal.ZERO)
                .status(Account.Status.ACTIVE)
                .systemAccount(false)
                .build();

        account = accountRepository.save(account);

        if (openingBalance != null && openingBalance.compareTo(BigDecimal.ZERO) > 0) {
            paymentService.deposit(account.getId(), openingBalance, "open-" + account.getId());
            account = accountRepository.findById(account.getId()).orElse(account);
        }

        return account;
    }

    @Transactional(readOnly = true)
    public Account getAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + accountId));
    }

    @Transactional(readOnly = true)
    public Account getByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + accountNumber));
    }

    @Transactional(readOnly = true)
    public List<Account> getAllAccounts() {
        return accountRepository.findBySystemAccountFalseOrderByCreatedAtDesc();
    }

    @Transactional
    public Account updateStatus(UUID accountId, Account.Status newStatus) {
        Account account = getAccount(accountId);
        account.setStatus(newStatus);
        return accountRepository.save(account);
    }
}
