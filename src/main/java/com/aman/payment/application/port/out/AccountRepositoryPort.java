package com.aman.payment.application.port.out;

import com.aman.payment.domain.model.Account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepositoryPort {

    Account save(Account account);

    Optional<Account> findById(UUID accountId);

    Optional<Account> findByIdForUpdate(UUID accountId);

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findNonSystemAccounts();

    List<Account> findAll();
}
