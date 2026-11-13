package com.aman.payment.infrastructure.idempotency;

import com.aman.payment.application.port.out.IdempotencyPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IdempotencyAdapter implements IdempotencyPort {

    private final IdempotencyKeyService idempotencyKeyService;

    @Override
    public Decision evaluate(String idempotencyKey, String payload) {
        IdempotencyKeyService.LookupOutcome outcome = idempotencyKeyService.checkAndLock(idempotencyKey, payload);

        return switch (outcome.result()) {
            case PROCEED -> new Decision(Outcome.PROCEED, Optional.empty());
            case ALREADY_DONE -> new Decision(Outcome.RETURN_CACHED,
                    outcome.record().map(r -> r.getResponseBody()));
            case IN_FLIGHT -> new Decision(Outcome.CONFLICT_IN_FLIGHT, Optional.empty());
        };
    }

    @Override
    public void begin(String idempotencyKey, String payload) {
        idempotencyKeyService.beginProcessing(idempotencyKey, payload);
    }

    @Override
    public void complete(String idempotencyKey, String responseBody) {
        idempotencyKeyService.completeProcessing(idempotencyKey, responseBody);
    }

    @Override
    public void fail(String idempotencyKey) {
        idempotencyKeyService.failProcessing(idempotencyKey);
    }
}
