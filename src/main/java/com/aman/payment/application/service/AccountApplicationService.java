package com.aman.payment.application.service;

import com.aman.payment.application.port.in.ManageAccountUseCase;
import com.aman.payment.application.port.in.ProcessPaymentUseCase;
import com.aman.payment.application.port.out.AccountRepositoryPort;
import com.aman.payment.domain.model.Account;
import com.aman.payment.domain.model.AccountStatus;
import com.aman.payment.domain.model.Money;
import com.aman.payment.domain.model.Payment;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class AccountApplicationService implements ManageAccountUseCase {

    private final AccountRepositoryPort accountRepository;
    private final ProcessPaymentUseCase processPaymentUseCase;

    public AccountApplicationService(AccountRepositoryPort accountRepository,
                                     @Lazy ProcessPaymentUseCase processPaymentUseCase) {
        this.accountRepository = accountRepository;
        this.processPaymentUseCase = processPaymentUseCase;
    }

    @Override
    @Transactional
    public Account openAccount(String accountNumber, String ownerName, String currency, Money openingBalance) {
        accountRepository.findByAccountNumber(accountNumber).ifPresent(a -> {
            throw new IllegalArgumentException("Account number already exists: " + accountNumber);
        });

        Account account = Account.create(accountNumber, ownerName, currency, Money.zero(currency));
        account = accountRepository.save(account);

        if (openingBalance != null && openingBalance.isPositive()) {
            deposit(account.getId(), openingBalance, "open-" + account.getId());
            account = accountRepository.findById(account.getId()).orElse(account);
        }

        return account;
    }

    @Override
    @Transactional(readOnly = true)
    public Account getAccountById(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + accountId));
    }

    @Override
    @Transactional(readOnly = true)
    public Account getAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + accountNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> getAllAccounts() {
        return accountRepository.findNonSystemAccounts();
    }

    @Override
    @Transactional
    public Account updateStatus(UUID accountId, AccountStatus status) {
        Account account = getAccountById(accountId);
        account.updateStatus(status);
        return accountRepository.save(account);
    }

    @Override
    public Payment deposit(UUID accountId, Money amount, String idempotencyKey) {
        Account destination = getAccountById(accountId);
        Account systemAccount = getOrCreateSystemAccount(destination.getCurrency());

        ProcessPaymentUseCase.ProcessPaymentCommand command = new ProcessPaymentUseCase.ProcessPaymentCommand(
                idempotencyKey,
                systemAccount.getId(),
                destination.getId(),
                amount
        );
        return processPaymentUseCase.processPayment(command);
    }

    @Override
    public Payment withdraw(UUID accountId, Money amount, String idempotencyKey) {
        Account source = getAccountById(accountId);
        Account systemAccount = getOrCreateSystemAccount(source.getCurrency());

        ProcessPaymentUseCase.ProcessPaymentCommand command = new ProcessPaymentUseCase.ProcessPaymentCommand(
                idempotencyKey,
                source.getId(),
                systemAccount.getId(),
                amount
        );
        return processPaymentUseCase.processPayment(command);
    }

    @Override
    @Transactional
    public Account getOrCreateSystemAccount(String currency) {
        String sysAccNum = "SYSTEM_RESERVE_" + currency.toUpperCase();
        return accountRepository.findByAccountNumber(sysAccNum)
                .orElseGet(() -> accountRepository.save(
                        Account.createSystemAccount(currency, Money.of(new BigDecimal("1000000000.0000"), currency))));
    }
}
