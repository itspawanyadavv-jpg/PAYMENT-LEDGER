package com.aman.payment.presentation.v1.dto;

import com.aman.payment.domain.model.Payment;
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
public class PaymentResponse {

    private UUID paymentId;
    private String idempotencyKey;
    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String failureReason;
    private Instant createdAt;

    public static PaymentResponse from(Payment payment) {
        if (payment == null) return null;
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .idempotencyKey(payment.getIdempotencyKey())
                .sourceAccountId(payment.getSourceAccountId())
                .destinationAccountId(payment.getDestinationAccountId())
                .amount(payment.getMoney().getAmount())
                .currency(payment.getMoney().getCurrencyCode())
                .status(payment.getStatus().name())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
