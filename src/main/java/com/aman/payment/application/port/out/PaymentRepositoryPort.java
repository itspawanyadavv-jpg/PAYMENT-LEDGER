package com.aman.payment.application.port.out;

import com.aman.payment.domain.model.Payment;
import com.aman.payment.domain.model.PaymentStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepositoryPort {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID paymentId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findAllOrderByCreatedAtDesc();

    long count();
}
