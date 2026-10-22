package com.aman.payment.service;

import com.aman.payment.dto.PaymentRequest;
import com.aman.payment.dto.PaymentResponse;
import com.aman.payment.idempotency.IdempotencyKeyService;
import com.aman.payment.idempotency.IdempotencyRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Thin, PaymentService-facing wrapper that translates between DTOs and
 * the lower-level IdempotencyKeyService (which knows nothing about
 * Payment-specific types and could be reused for other write
 * endpoints).
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyService idempotencyKeyService;
    private final ObjectMapper objectMapper;

    public enum Outcome {
        PROCEED, RETURN_CACHED, CONFLICT_IN_FLIGHT
    }

    public record Decision(Outcome outcome, Optional<PaymentResponse> cachedResponse) {
    }

    public Decision evaluate(PaymentRequest request) {
        String payload = serialize(request);
        IdempotencyKeyService.LookupOutcome outcome = idempotencyKeyService.checkAndLock(
                request.getIdempotencyKey(), payload);

        return switch (outcome.result()) {
            case PROCEED -> new Decision(Outcome.PROCEED, Optional.empty());
            case ALREADY_DONE -> new Decision(Outcome.RETURN_CACHED,
                    outcome.record().map(r -> deserialize(r.getResponseBody())));
            case IN_FLIGHT -> new Decision(Outcome.CONFLICT_IN_FLIGHT, Optional.empty());
        };
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void begin(PaymentRequest request) {
        idempotencyKeyService.beginProcessing(request.getIdempotencyKey(), serialize(request));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(String idempotencyKey, PaymentResponse response) {
        idempotencyKeyService.completeProcessing(idempotencyKey, serialize(response));
    }

    public void fail(String idempotencyKey) {
        idempotencyKeyService.failProcessing(idempotencyKey);
    }

    private String serialize(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize idempotency payload", e);
        }
    }

    private PaymentResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, PaymentResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize cached idempotency response", e);
        }
    }
}
