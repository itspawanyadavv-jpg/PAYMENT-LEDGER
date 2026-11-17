package com.aman.payment.application.port.out;

import java.util.Optional;

public interface IdempotencyPort {

    enum Outcome {
        PROCEED,
        RETURN_CACHED,
        CONFLICT_IN_FLIGHT
    }

    record Decision(Outcome outcome, Optional<String> cachedResponseBody) {}

    Decision evaluate(String idempotencyKey, String payload);

    void begin(String idempotencyKey, String payload);

    void complete(String idempotencyKey, String responseBody);

    void fail(String idempotencyKey);
}
