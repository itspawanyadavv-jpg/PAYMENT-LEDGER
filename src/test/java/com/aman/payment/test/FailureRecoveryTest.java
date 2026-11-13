package com.aman.payment.test;

import com.aman.payment.application.port.in.ManageAccountUseCase;
import com.aman.payment.application.port.in.ProcessPaymentUseCase;
import com.aman.payment.application.port.out.PaymentRepositoryPort;
import com.aman.payment.domain.exception.InsufficientBalanceException;
import com.aman.payment.domain.model.Account;
import com.aman.payment.domain.model.Money;
import com.aman.payment.domain.model.Payment;
import com.aman.payment.domain.model.PaymentStatus;
import com.aman.payment.infrastructure.messaging.outbox.OutboxPublisher;
import com.aman.payment.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.aman.payment.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@DisplayName("Failure and recovery behaviour")
class FailureRecoveryTest extends AbstractIntegrationTest {

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private ManageAccountUseCase manageAccountUseCase;

    @Autowired
    private PaymentRepositoryPort paymentRepository;

    @Autowired
    private SpringDataOutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Test
    @DisplayName("A successful payment's outbox events eventually reach PUBLISHED status")
    void successfulPayment_outboxEventsEventuallyPublish() {
        Account source = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Alice", "USD", Money.of("200.00", "USD"));
        Account destination = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Bob", "USD", Money.of("0.00", "USD"));

        Payment payment = processPaymentUseCase.processPayment(
                new ProcessPaymentUseCase.ProcessPaymentCommand(
                        "recovery-" + UUID.randomUUID(),
                        source.getId(),
                        destination.getId(),
                        Money.of("75.00", "USD")
                ));

        List<OutboxEventJpaEntity> events = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(payment.getId()))
                .toList();
        // PAYMENT_INITIATED and PAYMENT_COMPLETED
        assertThat(events).hasSize(2);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            outboxPublisher.publishPendingEvents();
            List<OutboxEventJpaEntity> refreshed = outboxEventRepository.findAll().stream()
                    .filter(e -> e.getAggregateId().equals(payment.getId()))
                    .toList();
            assertThat(refreshed).allMatch(e -> e.getStatus() == OutboxEventJpaEntity.Status.PUBLISHED);
        });
    }

    @Test
    @DisplayName("A failed payment attempt leaves no Payment row and no outbox events")
    void failedPayment_leavesNoOrphanedState() {
        Account source = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Poor", "USD", Money.of("5.00", "USD"));
        Account destination = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Rich", "USD", Money.of("0.00", "USD"));
        String key = "recovery-fail-" + UUID.randomUUID();

        ProcessPaymentUseCase.ProcessPaymentCommand command = new ProcessPaymentUseCase.ProcessPaymentCommand(
                key,
                source.getId(),
                destination.getId(),
                Money.of("1000.00", "USD")
        );

        assertThatThrownBy(() -> processPaymentUseCase.processPayment(command))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(paymentRepository.findByIdempotencyKey(key)).isEmpty();
        assertThat(outboxEventRepository.findAll()).noneMatch(e -> e.getEventType().contains(key));
    }

    @Test
    @DisplayName("A failed idempotency key can be retried with a corrected request")
    void failedIdempotencyKey_canBeRetriedAfterCorrection() {
        Account source = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Alice", "USD", Money.of("10.00", "USD"));
        Account destination = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Bob", "USD", Money.of("0.00", "USD"));
        String key = "recovery-retry-" + UUID.randomUUID();

        ProcessPaymentUseCase.ProcessPaymentCommand tooMuch = new ProcessPaymentUseCase.ProcessPaymentCommand(
                key,
                source.getId(),
                destination.getId(),
                Money.of("500.00", "USD")
        );
        assertThatThrownBy(() -> processPaymentUseCase.processPayment(tooMuch))
                .isInstanceOf(InsufficientBalanceException.class);

        // Top up source account
        Account funding = manageAccountUseCase.openAccount(
                "ACC-FUND-" + UUID.randomUUID(), "Funding", "USD", Money.of("1000.00", "USD"));
        processPaymentUseCase.processPayment(new ProcessPaymentUseCase.ProcessPaymentCommand(
                "fund-" + UUID.randomUUID(),
                funding.getId(),
                source.getId(),
                Money.of("500.00", "USD")
        ));

        ProcessPaymentUseCase.ProcessPaymentCommand corrected = new ProcessPaymentUseCase.ProcessPaymentCommand(
                key,
                source.getId(),
                destination.getId(),
                Money.of("500.00", "USD")
        );

        Payment response = processPaymentUseCase.processPayment(corrected);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }
}
