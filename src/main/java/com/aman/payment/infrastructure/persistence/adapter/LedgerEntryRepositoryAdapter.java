package com.aman.payment.infrastructure.persistence.adapter;

import com.aman.payment.application.port.out.LedgerEntryRepositoryPort;
import com.aman.payment.domain.model.LedgerEntry;
import com.aman.payment.infrastructure.persistence.entity.LedgerEntryJpaEntity;
import com.aman.payment.infrastructure.persistence.mapper.LedgerEntryJpaMapper;
import com.aman.payment.infrastructure.persistence.repository.SpringDataLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LedgerEntryRepositoryAdapter implements LedgerEntryRepositoryPort {

    private final SpringDataLedgerEntryRepository springDataLedgerEntryRepository;
    private final LedgerEntryJpaMapper ledgerEntryJpaMapper;

    @Override
    public LedgerEntry save(LedgerEntry entry) {
        LedgerEntryJpaEntity entity = ledgerEntryJpaMapper.toJpaEntity(entry);
        LedgerEntryJpaEntity saved = springDataLedgerEntryRepository.save(entity);
        return ledgerEntryJpaMapper.toDomain(saved);
    }

    @Override
    public List<LedgerEntry> saveAll(List<LedgerEntry> entries) {
        List<LedgerEntryJpaEntity> entities = entries.stream()
                .map(ledgerEntryJpaMapper::toJpaEntity)
                .toList();
        return springDataLedgerEntryRepository.saveAll(entities).stream()
                .map(ledgerEntryJpaMapper::toDomain)
                .toList();
    }

    @Override
    public List<LedgerEntry> findByAccountId(UUID accountId) {
        return springDataLedgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(ledgerEntryJpaMapper::toDomain)
                .toList();
    }

    @Override
    public List<LedgerEntry> findByPaymentId(UUID paymentId) {
        return springDataLedgerEntryRepository.findByPaymentId(paymentId).stream()
                .map(ledgerEntryJpaMapper::toDomain)
                .toList();
    }

    @Override
    public BigDecimal sumNetAmountForPayment(UUID paymentId) {
        return springDataLedgerEntryRepository.sumNetAmountForPayment(paymentId);
    }

    @Override
    public BigDecimal computeBalanceFromEntries(UUID accountId) {
        return springDataLedgerEntryRepository.computeBalanceFromEntries(accountId);
    }

    @Override
    public List<LedgerEntry> findRecent(int limit) {
        return springDataLedgerEntryRepository.findAll(
                PageRequest.of(0, limit, Sort.by("createdAt").descending()))
                .getContent().stream()
                .map(ledgerEntryJpaMapper::toDomain)
                .toList();
    }
}
