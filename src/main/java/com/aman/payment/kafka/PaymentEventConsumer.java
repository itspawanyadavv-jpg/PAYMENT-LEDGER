package com.aman.payment.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Example downstream consumer (e.g. for notifications, analytics, or a
 * read-model projection). Manual acknowledgment means the offset only
 * commits after processing succeeds, giving at-least-once delivery -
 * so handlers here must be idempotent themselves (e.g. keyed upserts by
 * eventId) since the same event may be redelivered after a crash.
 */
@Component
@Slf4j
public class PaymentEventConsumer {

    @KafkaListener(topics = "${payment.kafka.topic.payment-events:payment.events}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentEvent(PaymentEvent event, Acknowledgment acknowledgment) {
        try {
            log.info("Received payment event {} type={} payment={} status={}",
                    event.getEventId(), event.getEventType(), event.getPaymentId(), event.getStatus());

            // Idempotent handling: downstream systems should dedupe on eventId.
            handle(event);

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment event {}, will be redelivered", event.getEventId(), e);
            // Do not acknowledge - offset stays uncommitted and the broker redelivers.
        }
    }

    private void handle(PaymentEvent event) {
        // Placeholder for real downstream work: notifications, fraud scoring,
        // read-model projections, etc. Kept intentionally simple here.
    }
}
