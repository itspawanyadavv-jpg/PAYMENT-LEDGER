package com.aman.payment.repository;

import com.aman.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findBySourceAccountIdOrDestinationAccountId(UUID sourceAccountId, UUID destinationAccountId);

    List<Payment> findByStatus(Payment.Status status);

    List<Payment> findAllByOrderByCreatedAtDesc();
}
