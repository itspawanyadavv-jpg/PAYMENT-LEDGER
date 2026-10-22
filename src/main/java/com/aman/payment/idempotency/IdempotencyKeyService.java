package com.aman.payment.idempotency;

import com.aman.payment.exception.DuplicatePaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Two-tier idempotency guard:
 *
 * 1. Redis SETNX gives a cheap, low-latency distributed lock so that if
 *    two requests with the same key arrive within milliseconds of each
 *    other (common with client retry-on-timeout logic racing the
 *    original request), only one proceeds to do the actual work while
 *    the other is told to back off/poll.
 * 2. The idempotency_records table is the durable source of truth,
 *    written in the SAME transaction as the business change, so a
 *    completed idempotency record can never exist without the payment
 *    it protects, and vice versa.
 *
 * If the same key is reused with a DIFFERENT request payload (different
 * hash), that's treated as a client error, not a retry - it's a key
 * collision - and a DuplicatePaymentException is raised.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyKeyService {

    private static final String LOCK_PREFIX = "idempotency:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final IdempotencyRecordRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${payment.idempotency.ttl-hours:24}")
    private long ttlHours;

    public enum CheckResult {
        PROCEED,        // no prior record - go ahead and do the work
        ALREADY_DONE,   // COMPLETED record exists with matching hash - return cached response
        IN_FLIGHT        // another request with this key is currently being processed
    }

    public record LookupOutcome(CheckResult result, Optional<IdempotencyRecord> record) {
    }

    /**
     * Attempts to acquire the idempotency lock and returns what the
     * caller should do next. Does not itself run in a transaction -
     * the Redis lock happens outside the DB transaction so a slow DB
     * transaction can't hold the lock indefinitely.
     */
    public LookupOutcome checkAndLock(String idempotencyKey, String requestPayload) {
        String requestHash = hash(requestPayload);

        Optional<IdempotencyRecord> existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new DuplicatePaymentException(idempotencyKey);
            }
            if (record.getStatus() == IdempotencyRecord.Status.COMPLETED) {
                return new LookupOutcome(CheckResult.ALREADY_DONE, Optional.of(record));
            }
            if (record.getStatus() == IdempotencyRecord.Status.IN_PROGRESS) {
                return new LookupOutcome(CheckResult.IN_FLIGHT, Optional.of(record));
            }
            // FAILED: fall through and allow a fresh attempt
        }

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_PREFIX + idempotencyKey, requestHash, LOCK_TTL.toSeconds(), TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(acquired)) {
            return new LookupOutcome(CheckResult.IN_FLIGHT, Optional.empty());
        }

        return new LookupOutcome(CheckResult.PROCEED, Optional.empty());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void beginProcessing(String idempotencyKey, String requestPayload) {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(hash(requestPayload))
                .status(IdempotencyRecord.Status.IN_PROGRESS)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofHours(ttlHours)))
                .build();
        repository.save(record);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeProcessing(String idempotencyKey, String responseBody) {
        repository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyRecord.Status.COMPLETED);
            record.setResponseBody(responseBody);
            repository.save(record);
        });
        releaseLock(idempotencyKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failProcessing(String idempotencyKey) {
        try {
            repository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
                record.setStatus(IdempotencyRecord.Status.FAILED);
                repository.save(record);
            });
        } catch (Exception ignored) {
        } finally {
            releaseLock(idempotencyKey);
        }
    }

    public void releaseLock(String idempotencyKey) {
        redisTemplate.delete(LOCK_PREFIX + idempotencyKey);
    }

    private String hash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash idempotency payload", e);
        }
    }
}
