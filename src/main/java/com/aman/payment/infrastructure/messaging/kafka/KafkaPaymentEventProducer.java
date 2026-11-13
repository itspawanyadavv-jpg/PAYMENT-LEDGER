package com.aman.payment.infrastructure.messaging.kafka;

import com.aman.payment.application.port.out.PaymentEventPublisherPort;
import com.aman.payment.domain.model.Payment;
import com.aman.payment.infrastructure.messaging.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KafkaPaymentEventProducer implements PaymentEventPublisherPort {

    private final OutboxService outboxService;

    @Override
    public void paymentInitiated(Payment payment) {
        emit(payment, PaymentEvent.Type.PAYMENT_INITIATED);
    }

    @Override
    public void paymentCompleted(Payment payment) {
        emit(payment, PaymentEvent.Type.PAYMENT_COMPLETED);
    }

    @Override
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
                .amount(payment.getMoney().getAmount())
                .currency(payment.getMoney().getCurrencyCode())
                .status(payment.getStatus().name())
                .failureReason(payment.getFailureReason())
                .occurredAt(Instant.now())
                .build();

        outboxService.record("PAYMENT", payment.getId(), type.name(), event);
    }
}
