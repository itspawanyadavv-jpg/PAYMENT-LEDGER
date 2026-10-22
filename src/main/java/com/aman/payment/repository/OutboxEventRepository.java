package com.aman.payment.repository;

import com.aman.payment.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, java.util.UUID> {

    /**
     * Locks the next batch of pending events so multiple OutboxPublisher
     * instances (in a scaled-out deployment) don't publish the same
     * event twice. SKIP LOCKED lets other instances move past rows
     * already claimed instead of blocking on them.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
            select * from outbox_events
            where status = 'PENDING'
            order by created_at asc
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("batchSize") int batchSize);

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.Status status);
}
