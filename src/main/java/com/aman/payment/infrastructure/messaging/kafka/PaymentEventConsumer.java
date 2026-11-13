package com.aman.payment.infrastructure.messaging.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentEventConsumer {

    @KafkaListener(topics = "${payment.kafka.topic.payment-events:payment.events}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onPaymentEvent(PaymentEvent event, Acknowledgment acknowledgment) {
        try {
            log.info("Received payment event {} type={} payment={} status={}",
                    event.getEventId(), event.getEventType(), event.getPaymentId(), event.getStatus());

            handle(event);

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment event {}, will be redelivered", event.getEventId(), e);
        }
    }

    private void handle(PaymentEvent event) {
        // Idempotent processing hook for downstream consumers
    }
}
