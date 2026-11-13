package com.aman.payment.infrastructure.persistence.mapper;

import com.aman.payment.domain.model.EntryType;
import com.aman.payment.domain.model.LedgerEntry;
import com.aman.payment.domain.model.Money;
import com.aman.payment.infrastructure.persistence.entity.LedgerEntryJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class LedgerEntryJpaMapper {

    public LedgerEntryJpaEntity toJpaEntity(LedgerEntry domain) {
        if (domain == null) return null;
        return LedgerEntryJpaEntity.builder()
                .id(domain.getId())
                .paymentId(domain.getPaymentId())
                .accountId(domain.getAccountId())
                .entryType(domain.getEntryType().name())
                .amount(domain.getAmount().getAmount())
                .currency(domain.getAmount().getCurrencyCode())
                .balanceAfter(domain.getBalanceAfter().getAmount())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public LedgerEntry toDomain(LedgerEntryJpaEntity entity) {
        if (entity == null) return null;
        Money amount = Money.of(entity.getAmount(), entity.getCurrency());
        Money balanceAfter = Money.of(entity.getBalanceAfter(), entity.getCurrency());
        EntryType entryType = EntryType.valueOf(entity.getEntryType());
        return new LedgerEntry(
                entity.getId(),
                entity.getPaymentId(),
                entity.getAccountId(),
                entryType,
                amount,
                balanceAfter,
                entity.getCreatedAt()
        );
    }
}
