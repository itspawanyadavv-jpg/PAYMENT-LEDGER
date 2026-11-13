package com.aman.payment.application.port.in;

import com.aman.payment.domain.model.Payment;

import java.util.List;
import java.util.UUID;

public interface GetPaymentUseCase {

    Payment getPaymentById(UUID paymentId);

    Payment getPaymentByIdempotencyKey(String idempotencyKey);

    List<Payment> getAllPayments();
}
