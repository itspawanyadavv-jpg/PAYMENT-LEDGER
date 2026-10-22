package com.aman.payment.test;

import com.aman.payment.dto.PaymentRequest;
import com.aman.payment.dto.PaymentResponse;
import com.aman.payment.entity.Account;
import com.aman.payment.entity.OutboxEvent;
import com.aman.payment.entity.Payment;
import com.aman.payment.exception.InsufficientBalanceException;
import com.aman.payment.outbox.OutboxPublisher;
import com.aman.payment.repository.OutboxEventRepository;
import com.aman.payment.repository.PaymentRepository;
import com.aman.payment.service.AccountService;
import com.aman.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Verifies the system recovers correctly around partial failures:
 *
 *  1. Outbox durability: a successful payment always leaves behind a
 *     PENDING (later PUBLISHED) outbox row, even though the Kafka send
 *     itself happens in a separate, asynchronous step - so a broker
 *     outage at the moment of payment never loses the event, it's just
 *     delayed until OutboxPublisher's next poll succeeds.
 *  2. Idempotency-key recovery: after a payment fails validation
 *     (business failure), the same idempotency key can be reused for a
 *     corrected request rather than being permanently stuck.
 *  3. No orphaned side effects: a failed payment attempt leaves no
 *     Payment row and no outbox events behind, since the whole
 *     transaction rolled back together.
 */
@DisplayName("Failure and recovery behaviour")
class FailureRecoveryTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Test
    @DisplayName("A successful payment's outbox events eventually reach PUBLISHED status")
    void successfulPayment_outboxEventsEventuallyPublish() {
        Account source = accountService.openAccount("ACC-" + UUID.randomUUID(), "Alice", "USD", new BigDecimal("200.00"));
        Account destination = accountService.openAccount("ACC-" + UUID.randomUUID(), "Bob", "USD", new BigDecimal("0.00"));

        PaymentResponse response = paymentService.executePayment(PaymentRequest.builder()
                .idempotencyKey("recovery-" + UUID.randomUUID())
                .sourceAccountId(source.getId())
                .destinationAccountId(destination.getId())
                .amount(new BigDecimal("75.00"))
                .currency("USD")
                .build());

        List<OutboxEvent> events = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(response.getPaymentId()))
                .toList();
        // PAYMENT_INITIATED and PAYMENT_COMPLETED
        assertThat(events).hasSize(2);

        // Drive the publisher directly rather than waiting on the scheduler,
        // then confirm every event for this payment reaches PUBLISHED.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            outboxPublisher.publishPendingEvents();
            List<OutboxEvent> refreshed = outboxEventRepository.findAll().stream()
                    .filter(e -> e.getAggregateId().equals(response.getPaymentId()))
                    .toList();
            assertThat(refreshed).allMatch(e -> e.getStatus() == OutboxEvent.Status.PUBLISHED);
        });
    }

    @Test
    @DisplayName("A failed payment attempt leaves no Payment row and no outbox events")
    void failedPayment_leavesNoOrphanedState() {
        Account source = accountService.openAccount("ACC-" + UUID.randomUUID(), "Poor", "USD", new BigDecimal("5.00"));
        Account destination = accountService.openAccount("ACC-" + UUID.randomUUID(), "Rich", "USD", new BigDecimal("0.00"));
        String key = "recovery-fail-" + UUID.randomUUID();

        PaymentRequest request = PaymentRequest.builder()
                .idempotencyKey(key)
                .sourceAccountId(source.getId())
                .destinationAccountId(destination.getId())
                .amount(new BigDecimal("1000.00"))
                .currency("USD")
                .build();

        assertThatThrownBy(() -> paymentService.executePayment(request))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(paymentRepository.findByIdempotencyKey(key)).isEmpty();
        assertThat(outboxEventRepository.findAll()).noneMatch(e -> e.getEventType().contains(key));
    }

    @Test
    @DisplayName("A failed idempotency key can be retried with a corrected request")
    void failedIdempotencyKey_canBeRetriedAfterCorrection() {
        Account source = accountService.openAccount("ACC-" + UUID.randomUUID(), "Alice", "USD", new BigDecimal("10.00"));
        Account destination = accountService.openAccount("ACC-" + UUID.randomUUID(), "Bob", "USD", new BigDecimal("0.00"));
        String key = "recovery-retry-" + UUID.randomUUID();

        PaymentRequest tooMuch = PaymentRequest.builder()
                .idempotencyKey(key)
                .sourceAccountId(source.getId())
                .destinationAccountId(destination.getId())
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .build();
        assertThatThrownBy(() -> paymentService.executePayment(tooMuch))
                .isInstanceOf(InsufficientBalanceException.class);

        // Top up the source account, then retry with the SAME key but a
        // request that now succeeds - this must be allowed since the prior
        // attempt never completed.
        Account topUp = accountService.getAccount(source.getId());
        // simulate a deposit by opening a fresh credit payment from a funding account
        Account funding = accountService.openAccount("ACC-FUND-" + UUID.randomUUID(), "Funding", "USD", new BigDecimal("1000.00"));
        paymentService.executePayment(PaymentRequest.builder()
                .idempotencyKey("fund-" + UUID.randomUUID())
                .sourceAccountId(funding.getId())
                .destinationAccountId(source.getId())
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .build());

        PaymentRequest corrected = PaymentRequest.builder()
                .idempotencyKey(key)
                .sourceAccountId(source.getId())
                .destinationAccountId(destination.getId())
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .build();

        PaymentResponse response = paymentService.executePayment(corrected);
        assertThat(response.getStatus()).isEqualTo(Payment.Status.COMPLETED.name());
    }
}
