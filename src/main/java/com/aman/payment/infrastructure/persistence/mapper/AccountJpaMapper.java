package com.aman.payment.infrastructure.persistence.mapper;

import com.aman.payment.domain.model.Account;
import com.aman.payment.domain.model.AccountStatus;
import com.aman.payment.domain.model.Money;
import com.aman.payment.infrastructure.persistence.entity.AccountJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class AccountJpaMapper {

    public AccountJpaEntity toJpaEntity(Account domain) {
        if (domain == null) return null;
        return AccountJpaEntity.builder()
                .id(domain.getId())
                .accountNumber(domain.getAccountNumber())
                .ownerName(domain.getOwnerName())
                .currency(domain.getCurrency())
                .balance(domain.getBalance().getAmount())
                .version(domain.getVersion())
                .status(domain.getStatus().name())
                .systemAccount(domain.isSystemAccount())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    public Account toDomain(AccountJpaEntity entity) {
        if (entity == null) return null;
        Money balance = Money.of(entity.getBalance(), entity.getCurrency());
        AccountStatus status = AccountStatus.valueOf(entity.getStatus());
        return new Account(
                entity.getId(),
                entity.getAccountNumber(),
                entity.getOwnerName(),
                entity.getCurrency(),
                balance,
                status,
                entity.isSystemAccount(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
