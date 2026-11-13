package com.aman.payment.application.port.in;

import com.aman.payment.domain.model.LedgerEntry;

import java.util.List;
import java.util.UUID;

public interface QueryLedgerUseCase {

    record Discrepancy(String type, String referenceId, String detail) {}

    List<LedgerEntry> getAccountHistory(UUID accountId);

    List<LedgerEntry> getEntriesForPayment(UUID paymentId);

    boolean isBalanced(UUID paymentId);

    List<LedgerEntry> getRecentEntries(int limit);

    List<Discrepancy> reconcileAll();
}
