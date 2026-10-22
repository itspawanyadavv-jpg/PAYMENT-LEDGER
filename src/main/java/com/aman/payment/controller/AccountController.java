package com.aman.payment.controller;

import com.aman.payment.dto.AccountResponse;
import com.aman.payment.dto.PaymentResponse;
import com.aman.payment.entity.Account;
import com.aman.payment.service.AccountService;
import com.aman.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account lifecycle, balances, deposits and withdrawals")
public class AccountController {

    private final AccountService accountService;
    private final PaymentService paymentService;

    @Operation(summary = "Open a new customer account")
    @PostMapping
    public ResponseEntity<AccountResponse> openAccount(@Valid @RequestBody OpenAccountRequest request) {
        Account account = accountService.openAccount(
                request.getAccountNumber(), request.getOwnerName(), request.getCurrency(), request.getOpeningBalance());
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @Operation(summary = "List all customer accounts")
    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        List<AccountResponse> accounts = accountService.getAllAccounts().stream()
                .map(AccountResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(accounts);
    }

    @Operation(summary = "Get account details by ID")
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(AccountResponse.from(accountService.getAccount(accountId)));
    }

    @Operation(summary = "Get account by account number")
    @GetMapping(params = "accountNumber")
    public ResponseEntity<AccountResponse> getByAccountNumber(@RequestParam String accountNumber) {
        return ResponseEntity.ok(AccountResponse.from(accountService.getByAccountNumber(accountNumber)));
    }

    @Operation(summary = "Deposit funds into account from Central Reserve")
    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<PaymentResponse> deposit(
            @PathVariable UUID accountId,
            @Valid @RequestBody FundRequest request) {
        PaymentResponse response = paymentService.deposit(accountId, request.getAmount(), request.getIdempotencyKey());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Withdraw funds from account to Central Reserve")
    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<PaymentResponse> withdraw(
            @PathVariable UUID accountId,
            @Valid @RequestBody FundRequest request) {
        PaymentResponse response = paymentService.withdraw(accountId, request.getAmount(), request.getIdempotencyKey());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update account status (ACTIVE, FROZEN, CLOSED)")
    @PatchMapping("/{accountId}/status")
    public ResponseEntity<AccountResponse> updateStatus(
            @PathVariable UUID accountId,
            @Valid @RequestBody StatusUpdateRequest request) {
        Account account = accountService.updateStatus(accountId, request.getStatus());
        return ResponseEntity.ok(AccountResponse.from(account));
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OpenAccountRequest {
        @NotBlank
        private String accountNumber;
        @NotBlank
        private String ownerName;
        @NotBlank
        private String currency;
        private BigDecimal openingBalance;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FundRequest {
        @NotBlank(message = "idempotencyKey is required")
        private String idempotencyKey;

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be positive")
        private BigDecimal amount;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusUpdateRequest {
        @NotNull(message = "status is required")
        private Account.Status status;
    }
}
