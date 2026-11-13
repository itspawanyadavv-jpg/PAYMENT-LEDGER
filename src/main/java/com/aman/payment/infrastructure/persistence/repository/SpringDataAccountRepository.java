package com.aman.payment.infrastructure.persistence.repository;

import com.aman.payment.infrastructure.persistence.entity.AccountJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, UUID> {

    Optional<AccountJpaEntity> findByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountJpaEntity a where a.id = :id")
    Optional<AccountJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    List<AccountJpaEntity> findBySystemAccountFalseOrderByCreatedAtDesc();

    List<AccountJpaEntity> findAllByOrderByCreatedAtDesc();
}
