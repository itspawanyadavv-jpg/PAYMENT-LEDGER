package com.aman.payment.repository;

import com.aman.payment.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    /**
     * Pessimistic write lock used when a strict, single-attempt guarantee
     * against concurrent debits is required (see DebitProcessor). Prefer
     * plain findById + optimistic @Version for most paths; use this only
     * on the hot path where lock contention is expected and a short wait
     * is preferable to repeated optimistic-lock retries.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    java.util.List<Account> findBySystemAccountFalseOrderByCreatedAtDesc();

    java.util.List<Account> findAllByOrderByCreatedAtDesc();
}
