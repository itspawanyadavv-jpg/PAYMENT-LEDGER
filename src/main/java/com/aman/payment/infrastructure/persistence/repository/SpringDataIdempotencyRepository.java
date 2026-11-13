package com.aman.payment.infrastructure.persistence.repository;

import com.aman.payment.infrastructure.persistence.entity.IdempotencyRecordJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SpringDataIdempotencyRepository extends JpaRepository<IdempotencyRecordJpaEntity, String> {

    Optional<IdempotencyRecordJpaEntity> findByIdempotencyKey(String idempotencyKey);
}
