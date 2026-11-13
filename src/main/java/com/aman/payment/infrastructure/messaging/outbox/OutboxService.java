package com.aman.payment.infrastructure.messaging.outbox;

import com.aman.payment.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.aman.payment.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final SpringDataOutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OutboxEventJpaEntity event = OutboxEventJpaEntity.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(json)
                    .status(OutboxEventJpaEntity.Status.PENDING)
                    .retryCount(0)
                    .build();
            outboxEventRepository.save(event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}
