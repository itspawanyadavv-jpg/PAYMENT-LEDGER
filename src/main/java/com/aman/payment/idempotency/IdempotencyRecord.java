package com.aman.payment.idempotency;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

/**
 * Durable record of an idempotency key's lifecycle, stored in Postgres
 * as the source of truth (Redis is a fast-path cache in front of this).
 *
 * Lifecycle: IN_PROGRESS is written before any side effects occur.
 * On success it transitions to COMPLETED with the response body cached
 * so retries can be answered without redoing the work. On failure it
 * transitions to FAILED, allowing the caller to safely retry with the
 * same key.
 */
@Entity
@Table(name = "idempotency_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord implements Serializable {

    public enum Status {
        IN_PROGRESS, COMPLETED, FAILED
    }

    @Id
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 128)
    private String requestHash;

    @Lob
    @Column(name = "response_body")
    private String responseBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
