package com.aman.payment.dto;

import com.aman.payment.entity.Account;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountResponse {

    private UUID accountId;
    private String accountNumber;
    private String ownerName;
    private String currency;
    private BigDecimal balance;
    private String status;
    private boolean systemAccount;

    public static AccountResponse from(Account account) {
        return AccountResponse.builder()
                .accountId(account.getId())
                .accountNumber(account.getAccountNumber())
                .ownerName(account.getOwnerName())
                .currency(account.getCurrency())
                .balance(account.getBalance())
                .status(account.getStatus() != null ? account.getStatus().name() : Account.Status.ACTIVE.name())
                .systemAccount(account.isSystemAccount())
                .build();
    }
}
