package com.aman.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox row. Written in the SAME database transaction as
 * the Payment/LedgerEntry rows, so the "payment happened" fact and the
 * "event to publish" fact can never diverge - either both commit or
 * neither does. OutboxPublisher polls PENDING rows separately and
 * publishes them to Kafka, marking them PUBLISHED on success.
 */
@Entity
@Table(name = "outbox_events")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    public enum Status {
        PENDING, PUBLISHED, FAILED
    }

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = Status.PENDING;
        }
    }

    public void markPublished() {
        this.status = Status.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void markFailed() {
        markFailed(5);
    }

    public void markFailed(int maxRetries) {
        this.retryCount++;
        if (this.retryCount >= maxRetries) {
            this.status = Status.FAILED;
        } else {
            this.status = Status.PENDING;
        }
    }
}
