package com.aman.payment.presentation.v1.dto;

import com.aman.payment.domain.model.LedgerEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntryResponse {

    private UUID entryId;
    private UUID paymentId;
    private UUID accountId;
    private String entryType;
    private BigDecimal amount;
    private String currency;
    private BigDecimal balanceAfter;
    private Instant createdAt;

    public static LedgerEntryResponse from(LedgerEntry entry) {
        if (entry == null) return null;
        return LedgerEntryResponse.builder()
                .entryId(entry.getId())
                .paymentId(entry.getPaymentId())
                .accountId(entry.getAccountId())
                .entryType(entry.getEntryType().name())
                .amount(entry.getAmount().getAmount())
                .currency(entry.getAmount().getCurrencyCode())
                .balanceAfter(entry.getBalanceAfter().getAmount())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
