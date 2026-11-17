package com.aman.payment.application.port.out;

import com.aman.payment.domain.model.Payment;

public interface PaymentEventPublisherPort {

    void paymentInitiated(Payment payment);

    void paymentCompleted(Payment payment);

    void paymentFailed(Payment payment);
}
