package com.aman.payment.presentation.v1;

import com.aman.payment.application.port.in.ManageAccountUseCase;
import com.aman.payment.domain.model.Account;
import com.aman.payment.domain.model.Money;
import com.aman.payment.domain.model.Payment;
import com.aman.payment.presentation.v1.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account lifecycle, balances, deposits and withdrawals")
public class AccountController {

    private final ManageAccountUseCase manageAccountUseCase;

    @Operation(summary = "Open a new customer account")
    @PostMapping
    public ResponseEntity<AccountResponse> openAccount(@Valid @RequestBody OpenAccountRequest request) {
        Money initial = request.getOpeningBalance() != null
                ? Money.of(request.getOpeningBalance(), request.getCurrency())
                : Money.zero(request.getCurrency());

        Account account = manageAccountUseCase.openAccount(
                request.getAccountNumber(), request.getOwnerName(), request.getCurrency(), initial);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @Operation(summary = "List all customer accounts")
    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        List<AccountResponse> accounts = manageAccountUseCase.getAllAccounts().stream()
                .map(AccountResponse::from)
                .toList();
        return ResponseEntity.ok(accounts);
    }

    @Operation(summary = "Get account details by ID")
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(AccountResponse.from(manageAccountUseCase.getAccountById(accountId)));
    }

    @Operation(summary = "Get account by account number")
    @GetMapping(params = "accountNumber")
    public ResponseEntity<AccountResponse> getByAccountNumber(@RequestParam String accountNumber) {
        return ResponseEntity.ok(AccountResponse.from(manageAccountUseCase.getAccountByNumber(accountNumber)));
    }

    @Operation(summary = "Deposit funds into account from Central Reserve")
    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<PaymentResponse> deposit(
            @PathVariable UUID accountId,
            @Valid @RequestBody FundRequest request) {
        Account target = manageAccountUseCase.getAccountById(accountId);
        Money amount = Money.of(request.getAmount(), target.getCurrency());
        Payment payment = manageAccountUseCase.deposit(accountId, amount, request.getIdempotencyKey());
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    @Operation(summary = "Withdraw funds from account to Central Reserve")
    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<PaymentResponse> withdraw(
            @PathVariable UUID accountId,
            @Valid @RequestBody FundRequest request) {
        Account target = manageAccountUseCase.getAccountById(accountId);
        Money amount = Money.of(request.getAmount(), target.getCurrency());
        Payment payment = manageAccountUseCase.withdraw(accountId, amount, request.getIdempotencyKey());
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    @Operation(summary = "Update account status (ACTIVE, FROZEN, CLOSED)")
    @PatchMapping("/{accountId}/status")
    public ResponseEntity<AccountResponse> updateStatus(
            @PathVariable UUID accountId,
            @Valid @RequestBody StatusUpdateRequest request) {
        Account account = manageAccountUseCase.updateStatus(accountId, request.getStatus());
        return ResponseEntity.ok(AccountResponse.from(account));
    }
}
