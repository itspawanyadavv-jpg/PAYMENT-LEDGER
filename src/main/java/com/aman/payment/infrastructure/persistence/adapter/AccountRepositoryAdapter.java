package com.aman.payment.infrastructure.persistence.adapter;

import com.aman.payment.application.port.out.AccountRepositoryPort;
import com.aman.payment.domain.model.Account;
import com.aman.payment.infrastructure.persistence.entity.AccountJpaEntity;
import com.aman.payment.infrastructure.persistence.mapper.AccountJpaMapper;
import com.aman.payment.infrastructure.persistence.repository.SpringDataAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccountRepositoryAdapter implements AccountRepositoryPort {

    private final SpringDataAccountRepository springDataAccountRepository;
    private final AccountJpaMapper accountJpaMapper;

    @Override
    public Account save(Account account) {
        AccountJpaEntity entity = accountJpaMapper.toJpaEntity(account);
        AccountJpaEntity saved = springDataAccountRepository.save(entity);
        return accountJpaMapper.toDomain(saved);
    }

    @Override
    public Optional<Account> findById(UUID accountId) {
        return springDataAccountRepository.findById(accountId)
                .map(accountJpaMapper::toDomain);
    }

    @Override
    public Optional<Account> findByIdForUpdate(UUID accountId) {
        return springDataAccountRepository.findByIdForUpdate(accountId)
                .map(accountJpaMapper::toDomain);
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return springDataAccountRepository.findByAccountNumber(accountNumber)
                .map(accountJpaMapper::toDomain);
    }

    @Override
    public List<Account> findNonSystemAccounts() {
        return springDataAccountRepository.findBySystemAccountFalseOrderByCreatedAtDesc().stream()
                .map(accountJpaMapper::toDomain)
                .toList();
    }

    @Override
    public List<Account> findAll() {
        return springDataAccountRepository.findAll().stream()
                .map(accountJpaMapper::toDomain)
                .toList();
    }
}
