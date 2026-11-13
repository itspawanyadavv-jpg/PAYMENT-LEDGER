package com.aman.payment.infrastructure.messaging.outbox;

import com.aman.payment.infrastructure.messaging.kafka.PaymentEvent;
import com.aman.payment.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.aman.payment.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final SpringDataOutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payment.outbox.batch-size:100}")
    private int batchSize;

    @Value("${payment.kafka.topic.payment-events:payment.events}")
    private String topic;

    @Scheduled(fixedDelayString = "${payment.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventJpaEntity> batch = outboxEventRepository.lockNextBatch(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEventJpaEntity event : batch) {
            try {
                PaymentEvent kafkaEvent = objectMapper.readValue(event.getPayload(), PaymentEvent.class);
                kafkaTemplate.send(topic, event.getAggregateId().toString(), kafkaEvent).get();
                event.markPublished();
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
                event.markFailed();
            }
            outboxEventRepository.save(event);
        }
    }
}
