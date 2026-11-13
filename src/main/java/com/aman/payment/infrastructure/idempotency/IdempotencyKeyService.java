package com.aman.payment.infrastructure.idempotency;

import com.aman.payment.domain.exception.DuplicatePaymentException;
import com.aman.payment.infrastructure.persistence.entity.IdempotencyRecordJpaEntity;
import com.aman.payment.infrastructure.persistence.repository.SpringDataIdempotencyRepository;
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

@Service
@RequiredArgsConstructor
public class IdempotencyKeyService {

    private static final String LOCK_PREFIX = "idempotency:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final SpringDataIdempotencyRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${payment.idempotency.ttl-hours:24}")
    private long ttlHours;

    public enum CheckResult {
        PROCEED,
        ALREADY_DONE,
        IN_FLIGHT
    }

    public record LookupOutcome(CheckResult result, Optional<IdempotencyRecordJpaEntity> record) {}

    public LookupOutcome checkAndLock(String idempotencyKey, String requestPayload) {
        String requestHash = hash(requestPayload);

        Optional<IdempotencyRecordJpaEntity> existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyRecordJpaEntity record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new DuplicatePaymentException(idempotencyKey);
            }
            if (record.getStatus() == IdempotencyRecordJpaEntity.Status.COMPLETED) {
                return new LookupOutcome(CheckResult.ALREADY_DONE, Optional.of(record));
            }
            if (record.getStatus() == IdempotencyRecordJpaEntity.Status.IN_PROGRESS) {
                return new LookupOutcome(CheckResult.IN_FLIGHT, Optional.of(record));
            }
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
        IdempotencyRecordJpaEntity record = IdempotencyRecordJpaEntity.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(hash(requestPayload))
                .status(IdempotencyRecordJpaEntity.Status.IN_PROGRESS)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofHours(ttlHours)))
                .build();
        repository.save(record);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void completeProcessing(String idempotencyKey, String responseBody) {
        repository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
            record.setStatus(IdempotencyRecordJpaEntity.Status.COMPLETED);
            record.setResponseBody(responseBody);
            repository.save(record);
        });
        releaseLock(idempotencyKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failProcessing(String idempotencyKey) {
        try {
            repository.findByIdempotencyKey(idempotencyKey).ifPresent(record -> {
                record.setStatus(IdempotencyRecordJpaEntity.Status.FAILED);
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
