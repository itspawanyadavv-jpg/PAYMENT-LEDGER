package com.aman.payment.infrastructure.persistence.adapter;

import com.aman.payment.application.port.out.PaymentRepositoryPort;
import com.aman.payment.domain.model.Payment;
import com.aman.payment.domain.model.PaymentStatus;
import com.aman.payment.infrastructure.persistence.entity.PaymentJpaEntity;
import com.aman.payment.infrastructure.persistence.mapper.PaymentJpaMapper;
import com.aman.payment.infrastructure.persistence.repository.SpringDataPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepositoryPort {

    private final SpringDataPaymentRepository springDataPaymentRepository;
    private final PaymentJpaMapper paymentJpaMapper;

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity entity = paymentJpaMapper.toJpaEntity(payment);
        PaymentJpaEntity saved = springDataPaymentRepository.save(entity);
        return paymentJpaMapper.toDomain(saved);
    }

    @Override
    public Optional<Payment> findById(UUID paymentId) {
        return springDataPaymentRepository.findById(paymentId)
                .map(paymentJpaMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return springDataPaymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(paymentJpaMapper::toDomain);
    }

    @Override
    public List<Payment> findByStatus(PaymentStatus status) {
        return springDataPaymentRepository.findByStatus(status.name()).stream()
                .map(paymentJpaMapper::toDomain)
                .toList();
    }

    @Override
    public List<Payment> findAllOrderByCreatedAtDesc() {
        return springDataPaymentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(paymentJpaMapper::toDomain)
                .toList();
    }

    @Override
    public long count() {
        return springDataPaymentRepository.count();
    }
}
