package com.aman.payment.presentation.v1;

import com.aman.payment.application.port.in.QueryLedgerUseCase;
import com.aman.payment.presentation.v1.dto.LedgerEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Tag(name = "Ledger & Reconciliation", description = "Immutable audit entries, invariant verification, and drift reconciliation")
public class LedgerController {

    private final QueryLedgerUseCase queryLedgerUseCase;

    @Operation(summary = "Get account ledger entry history")
    @GetMapping("/accounts/{accountId}/entries")
    public ResponseEntity<List<LedgerEntryResponse>> getAccountHistory(@PathVariable UUID accountId) {
        List<LedgerEntryResponse> entries = queryLedgerUseCase.getAccountHistory(accountId).stream()
                .map(LedgerEntryResponse::from)
                .toList();
        return ResponseEntity.ok(entries);
    }

    @Operation(summary = "Get all ledger legs for a specific payment")
    @GetMapping("/payments/{paymentId}/entries")
    public ResponseEntity<List<LedgerEntryResponse>> getPaymentEntries(@PathVariable UUID paymentId) {
        List<LedgerEntryResponse> entries = queryLedgerUseCase.getEntriesForPayment(paymentId).stream()
                .map(LedgerEntryResponse::from)
                .toList();
        return ResponseEntity.ok(entries);
    }

    @Operation(summary = "Verify double-entry balance invariant for a payment")
    @GetMapping("/payments/{paymentId}/balanced")
    public ResponseEntity<Map<String, Object>> isBalanced(@PathVariable UUID paymentId) {
        boolean balanced = queryLedgerUseCase.isBalanced(paymentId);
        return ResponseEntity.ok(Map.of("paymentId", paymentId, "balanced", balanced));
    }

    @Operation(summary = "Get recent ledger entries")
    @GetMapping("/entries")
    public ResponseEntity<List<LedgerEntryResponse>> getRecentEntries(@RequestParam(defaultValue = "50") int limit) {
        List<LedgerEntryResponse> entries = queryLedgerUseCase.getRecentEntries(limit).stream()
                .map(LedgerEntryResponse::from)
                .toList();
        return ResponseEntity.ok(entries);
    }

    /**
     * Triggers an on-demand reconciliation pass (in addition to the hourly scheduled one).
     * Protected by Role-Based Access Control: requires FINANCE_ADMIN role.
     */
    @Operation(summary = "Trigger on-demand reconciliation audit (Requires FINANCE_ADMIN)")
    @PostMapping("/reconcile")
    @PreAuthorize("hasRole('FINANCE_ADMIN')")
    public ResponseEntity<List<QueryLedgerUseCase.Discrepancy>> reconcile() {
        return ResponseEntity.ok(queryLedgerUseCase.reconcileAll());
    }
}
