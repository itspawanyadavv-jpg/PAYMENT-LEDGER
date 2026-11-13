package com.aman.payment.application.port.in;

import com.aman.payment.domain.model.Money;
import com.aman.payment.domain.model.Payment;

import java.util.Objects;
import java.util.UUID;

public interface ProcessPaymentUseCase {

    Payment processPayment(ProcessPaymentCommand command);

    record ProcessPaymentCommand(
            String idempotencyKey,
            UUID sourceAccountId,
            UUID destinationAccountId,
            Money amount
    ) {
        public ProcessPaymentCommand {
            Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
            Objects.requireNonNull(sourceAccountId, "sourceAccountId cannot be null");
            Objects.requireNonNull(destinationAccountId, "destinationAccountId cannot be null");
            Objects.requireNonNull(amount, "amount cannot be null");
        }
    }
}
