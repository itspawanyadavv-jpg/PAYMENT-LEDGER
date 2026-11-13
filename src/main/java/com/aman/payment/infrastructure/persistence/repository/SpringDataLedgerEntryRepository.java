package com.aman.payment.infrastructure.persistence.repository;

import com.aman.payment.infrastructure.persistence.entity.LedgerEntryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataLedgerEntryRepository extends JpaRepository<LedgerEntryJpaEntity, UUID> {

    List<LedgerEntryJpaEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<LedgerEntryJpaEntity> findByPaymentId(UUID paymentId);

    @Query("""
           select coalesce(sum(case when e.entryType = 'DEBIT' then e.amount else -e.amount end), 0)
           from LedgerEntryJpaEntity e where e.paymentId = :paymentId
           """)
    BigDecimal sumNetAmountForPayment(@Param("paymentId") UUID paymentId);

    @Query("""
           select coalesce(sum(case when e.entryType = 'CREDIT' then e.amount else -e.amount end), 0)
           from LedgerEntryJpaEntity e where e.accountId = :accountId
           """)
    BigDecimal computeBalanceFromEntries(@Param("accountId") UUID accountId);
}
