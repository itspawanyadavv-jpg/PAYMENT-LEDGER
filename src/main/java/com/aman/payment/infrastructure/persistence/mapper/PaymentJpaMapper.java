package com.aman.payment.infrastructure.persistence.mapper;

import com.aman.payment.domain.model.Money;
import com.aman.payment.domain.model.Payment;
import com.aman.payment.domain.model.PaymentStatus;
import com.aman.payment.infrastructure.persistence.entity.PaymentJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentJpaMapper {

    public PaymentJpaEntity toJpaEntity(Payment domain) {
        if (domain == null) return null;
        return PaymentJpaEntity.builder()
                .id(domain.getId())
                .idempotencyKey(domain.getIdempotencyKey())
                .sourceAccountId(domain.getSourceAccountId())
                .destinationAccountId(domain.getDestinationAccountId())
                .amount(domain.getMoney().getAmount())
                .currency(domain.getMoney().getCurrencyCode())
                .status(domain.getStatus().name())
                .failureReason(domain.getFailureReason())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    public Payment toDomain(PaymentJpaEntity entity) {
        if (entity == null) return null;
        Money money = Money.of(entity.getAmount(), entity.getCurrency());
        PaymentStatus status = PaymentStatus.valueOf(entity.getStatus());
        return new Payment(
                entity.getId(),
                entity.getIdempotencyKey(),
                entity.getSourceAccountId(),
                entity.getDestinationAccountId(),
                money,
                status,
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
