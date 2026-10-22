package com.aman.payment.dto;

import com.aman.payment.entity.LedgerEntry;
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
        return LedgerEntryResponse.builder()
                .entryId(entry.getId())
                .paymentId(entry.getPaymentId())
                .accountId(entry.getAccountId())
                .entryType(entry.getEntryType().name())
                .amount(entry.getAmount())
                .currency(entry.getCurrency())
                .balanceAfter(entry.getBalanceAfter())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
