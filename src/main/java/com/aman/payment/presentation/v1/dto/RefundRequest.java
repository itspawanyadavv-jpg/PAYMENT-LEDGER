package com.aman.payment.presentation.v1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundRequest {
    @NotBlank(message = "idempotencyKey is required")
    private String idempotencyKey;
}
