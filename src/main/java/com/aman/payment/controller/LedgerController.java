package com.aman.payment.controller;

import com.aman.payment.dto.LedgerEntryResponse;
import com.aman.payment.service.LedgerService;
import com.aman.payment.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Ledger & Reconciliation", description = "Immutable audit entries, invariant verification, and drift reconciliation")
public class LedgerController {

    private final LedgerService ledgerService;
    private final ReconciliationService reconciliationService;

    @GetMapping("/accounts/{accountId}/entries")
    public ResponseEntity<List<LedgerEntryResponse>> getAccountHistory(@PathVariable UUID accountId) {
        List<LedgerEntryResponse> entries = ledgerService.getAccountHistory(accountId).stream()
                .map(LedgerEntryResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/payments/{paymentId}/entries")
    public ResponseEntity<List<LedgerEntryResponse>> getPaymentEntries(@PathVariable UUID paymentId) {
        List<LedgerEntryResponse> entries = ledgerService.getEntriesForPayment(paymentId).stream()
                .map(LedgerEntryResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/payments/{paymentId}/balanced")
    public ResponseEntity<Map<String, Object>> isBalanced(@PathVariable UUID paymentId) {
        boolean balanced = ledgerService.isBalanced(paymentId);
        return ResponseEntity.ok(Map.of("paymentId", paymentId, "balanced", balanced));
    }

    @GetMapping("/entries")
    public ResponseEntity<List<LedgerEntryResponse>> getRecentEntries(@RequestParam(defaultValue = "50") int limit) {
        List<LedgerEntryResponse> entries = ledgerService.getRecentEntries(limit).stream()
                .map(LedgerEntryResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(entries);
    }

    /**
     * Triggers an on-demand reconciliation pass (in addition to the
     * hourly scheduled one) and returns any discrepancies found.
     */
    @PostMapping("/reconcile")
    public ResponseEntity<List<ReconciliationService.Discrepancy>> reconcile() {
        return ResponseEntity.ok(reconciliationService.reconcileAll());
    }
}
