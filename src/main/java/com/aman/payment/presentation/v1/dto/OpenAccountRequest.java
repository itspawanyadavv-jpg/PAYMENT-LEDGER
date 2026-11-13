package com.aman.payment.presentation.v1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpenAccountRequest {
    @NotBlank
    private String accountNumber;
    @NotBlank
    private String ownerName;
    @NotBlank
    private String currency;
    private BigDecimal openingBalance;
}
