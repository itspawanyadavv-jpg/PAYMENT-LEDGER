package com.aman.payment.controller;

import com.aman.payment.dto.PaymentRequest;
import com.aman.payment.dto.PaymentResponse;
import com.aman.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

    private final PaymentService paymentService;

    /**
     * Initiates a payment. Callers MUST send a unique idempotencyKey and
     * should retry with the SAME key (not a new one) on timeouts or
     * connection failures - that's what makes the retry safe.
     */
    @Operation(summary = "Initiate an idempotent double-entry payment transfer")
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.executePayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List all recent payment transfers")
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @Operation(summary = "Get payment details by ID")
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId));
    }

    @Operation(summary = "Get payment by client idempotency key")
    @GetMapping(params = "idempotencyKey")
    public ResponseEntity<PaymentResponse> getByIdempotencyKey(@RequestParam String idempotencyKey) {
        return ResponseEntity.ok(paymentService.getByIdempotencyKey(idempotencyKey));
    }

    @Operation(summary = "Refund an existing completed payment (posts reversing double-entry legs)")
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody RefundRequest request) {
        PaymentResponse response = paymentService.refundPayment(paymentId, request.getIdempotencyKey());
        return ResponseEntity.ok(response);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RefundRequest {
        @NotBlank(message = "idempotencyKey is required")
        private String idempotencyKey;
    }
}
