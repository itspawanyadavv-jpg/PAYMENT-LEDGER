package com.aman.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inbound request to move money between two accounts. idempotencyKey
 * MUST be supplied by the caller (typically a client-generated UUID)
 * so retried requests - e.g. after a network timeout - are safely
 * deduplicated instead of double-spending.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {

    @NotBlank(message = "idempotencyKey is required")
    private String idempotencyKey;

    @NotNull(message = "sourceAccountId is required")
    private UUID sourceAccountId;

    @NotNull(message = "destinationAccountId is required")
    private UUID destinationAccountId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    private String currency;
}
