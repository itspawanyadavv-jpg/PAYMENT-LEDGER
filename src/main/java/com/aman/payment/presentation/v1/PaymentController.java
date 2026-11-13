package com.aman.payment.presentation.v1;

import com.aman.payment.application.port.in.GetPaymentUseCase;
import com.aman.payment.application.port.in.ProcessPaymentUseCase;
import com.aman.payment.application.port.in.RefundPaymentUseCase;
import com.aman.payment.domain.model.Money;
import com.aman.payment.domain.model.Payment;
import com.aman.payment.presentation.v1.dto.PaymentRequest;
import com.aman.payment.presentation.v1.dto.PaymentResponse;
import com.aman.payment.presentation.v1.dto.RefundRequest;
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
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Idempotent payment initiation, status checks, and refunds")
public class PaymentController {

    private final ProcessPaymentUseCase processPaymentUseCase;
    private final GetPaymentUseCase getPaymentUseCase;
    private final RefundPaymentUseCase refundPaymentUseCase;

    @Operation(summary = "Initiate an idempotent double-entry payment transfer")
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest request) {
        Money money = Money.of(request.getAmount(), request.getCurrency());
        ProcessPaymentUseCase.ProcessPaymentCommand command = new ProcessPaymentUseCase.ProcessPaymentCommand(
                request.getIdempotencyKey(),
                request.getSourceAccountId(),
                request.getDestinationAccountId(),
                money
        );
        Payment payment = processPaymentUseCase.processPayment(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(payment));
    }

    @Operation(summary = "List all recent payment transfers")
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getAllPayments() {
        List<PaymentResponse> responses = getPaymentUseCase.getAllPayments().stream()
                .map(PaymentResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Get payment details by ID")
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        Payment payment = getPaymentUseCase.getPaymentById(paymentId);
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    @Operation(summary = "Get payment by client idempotency key")
    @GetMapping(params = "idempotencyKey")
    public ResponseEntity<PaymentResponse> getByIdempotencyKey(@RequestParam String idempotencyKey) {
        Payment payment = getPaymentUseCase.getPaymentByIdempotencyKey(idempotencyKey);
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    @Operation(summary = "Refund an existing completed payment (posts reversing double-entry legs)")
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody RefundRequest request) {
        Payment refund = refundPaymentUseCase.refundPayment(paymentId, request.getIdempotencyKey());
        return ResponseEntity.ok(PaymentResponse.from(refund));
    }
}
