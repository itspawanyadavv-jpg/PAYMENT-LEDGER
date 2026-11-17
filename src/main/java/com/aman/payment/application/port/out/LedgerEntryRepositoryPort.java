package com.aman.payment.application.port.out;

import com.aman.payment.domain.model.LedgerEntry;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepositoryPort {

    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> saveAll(List<LedgerEntry> entries);

    List<LedgerEntry> findByAccountId(UUID accountId);

    List<LedgerEntry> findByPaymentId(UUID paymentId);

    BigDecimal sumNetAmountForPayment(UUID paymentId);

    BigDecimal computeBalanceFromEntries(UUID accountId);

    List<LedgerEntry> findRecent(int limit);
}
