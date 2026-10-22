package com.aman.payment.kafka;

import com.aman.payment.entity.Payment;
import com.aman.payment.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds PaymentEvent payloads and hands them to OutboxService for
 * transactional persistence. Notably, this class never talks to Kafka
 * directly - the actual send happens asynchronously via
 * OutboxPublisher - so calling this from within a business transaction
 * is always safe and never blocks on broker I/O.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final OutboxService outboxService;

    public void paymentInitiated(Payment payment) {
        emit(payment, PaymentEvent.Type.PAYMENT_INITIATED);
    }

    public void paymentCompleted(Payment payment) {
        emit(payment, PaymentEvent.Type.PAYMENT_COMPLETED);
    }

    public void paymentFailed(Payment payment) {
        emit(payment, PaymentEvent.Type.PAYMENT_FAILED);
    }

    private void emit(Payment payment, PaymentEvent.Type type) {
        PaymentEvent event = PaymentEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(type)
                .paymentId(payment.getId())
                .idempotencyKey(payment.getIdempotencyKey())
                .sourceAccountId(payment.getSourceAccountId())
                .destinationAccountId(payment.getDestinationAccountId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus().name())
                .failureReason(payment.getFailureReason())
                .occurredAt(Instant.now())
                .build();

        outboxService.record("PAYMENT", payment.getId(), type.name(), event);
    }
}
