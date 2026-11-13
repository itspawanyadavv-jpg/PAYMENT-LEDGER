package com.aman.payment.infrastructure.persistence.repository;

import com.aman.payment.infrastructure.persistence.entity.PaymentJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataPaymentRepository extends JpaRepository<PaymentJpaEntity, UUID> {

    Optional<PaymentJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<PaymentJpaEntity> findBySourceAccountIdOrDestinationAccountId(UUID sourceAccountId, UUID destinationAccountId);

    List<PaymentJpaEntity> findByStatus(String status);

    List<PaymentJpaEntity> findAllByOrderByCreatedAtDesc();
}
