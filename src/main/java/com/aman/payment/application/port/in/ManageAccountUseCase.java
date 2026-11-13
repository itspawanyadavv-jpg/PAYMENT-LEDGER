package com.aman.payment.application.port.in;

import com.aman.payment.domain.model.Account;
import com.aman.payment.domain.model.AccountStatus;
import com.aman.payment.domain.model.Money;
import com.aman.payment.domain.model.Payment;

import java.util.List;
import java.util.UUID;

public interface ManageAccountUseCase {

    Account openAccount(String accountNumber, String ownerName, String currency, Money openingBalance);

    Account getAccountById(UUID accountId);

    Account getAccountByNumber(String accountNumber);

    List<Account> getAllAccounts();

    Account updateStatus(UUID accountId, AccountStatus status);

    Payment deposit(UUID accountId, Money amount, String idempotencyKey);

    Payment withdraw(UUID accountId, Money amount, String idempotencyKey);

    Account getOrCreateSystemAccount(String currency);
}
