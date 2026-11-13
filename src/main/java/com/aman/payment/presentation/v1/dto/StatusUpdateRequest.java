package com.aman.payment.presentation.v1.dto;

import com.aman.payment.domain.model.AccountStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StatusUpdateRequest {
    @NotNull(message = "status is required")
    private AccountStatus status;
}
