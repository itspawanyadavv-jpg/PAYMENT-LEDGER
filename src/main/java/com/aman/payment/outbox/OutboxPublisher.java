package com.aman.payment.outbox;

import com.aman.payment.entity.OutboxEvent;
import com.aman.payment.kafka.PaymentEvent;
import com.aman.payment.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polls the outbox table on a fixed interval, publishes PENDING rows to
 * Kafka, and marks them PUBLISHED once the broker acknowledges. Uses
 * SELECT ... FOR UPDATE SKIP LOCKED (see OutboxEventRepository) so
 * multiple instances of this service can run concurrently without
 * double-publishing.
 *
 * Failure handling: if publishing throws, the row is marked FAILED with
 * an incremented retry_count and picked up again on a later poll (a
 * production system would add exponential backoff / a max-retry dead
 * letter path here).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payment.outbox.batch-size:100}")
    private int batchSize;

    @Value("${payment.kafka.topic.payment-events:payment.events}")
    private String topic;

    @Scheduled(fixedDelayString = "${payment.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> batch = outboxEventRepository.lockNextBatch(batchSize);
        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEvent event : batch) {
            try {
                PaymentEvent kafkaEvent = objectMapper.readValue(event.getPayload(), PaymentEvent.class);
                // Partition key = aggregateId keeps all events for one payment/account in order.
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
