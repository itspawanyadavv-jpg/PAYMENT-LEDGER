package com.aman.payment.kafka;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire format published to the payment.events Kafka topic. Kept
 * deliberately flat and self-contained (no lazy JPA associations) so
 * it serializes cleanly and downstream consumers don't need access to
 * this service's database.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {

    public enum Type {
        PAYMENT_INITIATED, PAYMENT_COMPLETED, PAYMENT_FAILED
    }

    private UUID eventId;
    private Type eventType;
    private UUID paymentId;
    private String idempotencyKey;
    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String failureReason;
    private Instant occurredAt;

    @JsonCreator
    public static PaymentEvent create(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("eventType") Type eventType,
            @JsonProperty("paymentId") UUID paymentId,
            @JsonProperty("idempotencyKey") String idempotencyKey,
            @JsonProperty("sourceAccountId") UUID sourceAccountId,
            @JsonProperty("destinationAccountId") UUID destinationAccountId,
            @JsonProperty("amount") BigDecimal amount,
            @JsonProperty("currency") String currency,
            @JsonProperty("status") String status,
            @JsonProperty("failureReason") String failureReason,
            @JsonProperty("occurredAt") Instant occurredAt) {
        return new PaymentEvent(eventId, eventType, paymentId, idempotencyKey, sourceAccountId,
                destinationAccountId, amount, currency, status, failureReason, occurredAt);
    }
}
