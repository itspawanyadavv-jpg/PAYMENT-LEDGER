package com.aman.payment.application.port.in;

import com.aman.payment.domain.model.Payment;

import java.util.UUID;

public interface RefundPaymentUseCase {

    Payment refundPayment(UUID originalPaymentId, String refundIdempotencyKey);
}
