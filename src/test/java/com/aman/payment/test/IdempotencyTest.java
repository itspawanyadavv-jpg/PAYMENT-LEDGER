package com.aman.payment.test;

import com.aman.payment.application.port.in.ManageAccountUseCase;
import com.aman.payment.application.port.in.ProcessPaymentUseCase;
import com.aman.payment.application.port.out.PaymentRepositoryPort;
import com.aman.payment.domain.exception.DuplicatePaymentException;
import com.aman.payment.domain.model.Account;
import com.aman.payment.domain.model.Money;
import com.aman.payment.domain.model.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Idempotency behaviour")
class IdempotencyTest extends AbstractIntegrationTest {

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private ManageAccountUseCase manageAccountUseCase;

    @Autowired
    private PaymentRepositoryPort paymentRepository;

    private Account source;
    private Account destination;

    @BeforeEach
    void setUp() {
        source = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Alice", "USD", Money.of("1000.00", "USD"));
        destination = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Bob", "USD", Money.of("0.00", "USD"));
    }

    @Test
    @DisplayName("Retrying with the same idempotency key returns the same result and does not double-move money")
    void sameKeySameRequest_isSafeToRetry() {
        String key = "idem-" + UUID.randomUUID();
        ProcessPaymentUseCase.ProcessPaymentCommand command = new ProcessPaymentUseCase.ProcessPaymentCommand(
                key,
                source.getId(),
                destination.getId(),
                Money.of("100.00", "USD")
        );

        Payment first = processPaymentUseCase.processPayment(command);
        Payment second = processPaymentUseCase.processPayment(command);

        assertThat(second.getId()).isEqualTo(first.getId());
        long matchingPayments = paymentRepository.findAllOrderByCreatedAtDesc().stream()
                .filter(p -> p.getIdempotencyKey().equals(key))
                .count();
        assertThat(matchingPayments).isEqualTo(1);

        Account refreshedDestination = manageAccountUseCase.getAccountById(destination.getId());
        assertThat(refreshedDestination.getBalance().getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Reusing an idempotency key with a different payload is rejected")
    void sameKeyDifferentRequest_isRejected() {
        String key = "idem-" + UUID.randomUUID();
        ProcessPaymentUseCase.ProcessPaymentCommand first = new ProcessPaymentUseCase.ProcessPaymentCommand(
                key,
                source.getId(),
                destination.getId(),
                Money.of("50.00", "USD")
        );

        processPaymentUseCase.processPayment(first);

        ProcessPaymentUseCase.ProcessPaymentCommand conflicting = new ProcessPaymentUseCase.ProcessPaymentCommand(
                key,
                source.getId(),
                destination.getId(),
                Money.of("999.00", "USD")
        );

        assertThatThrownBy(() -> processPaymentUseCase.processPayment(conflicting))
                .isInstanceOf(DuplicatePaymentException.class);
    }

    @Test
    @DisplayName("N concurrent requests with the same idempotency key result in exactly one payment")
    void concurrentRequestsSameKey_resultInExactlyOnePayment() throws InterruptedException {
        String key = "idem-" + UUID.randomUUID();
        int concurrency = 10;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);

        ProcessPaymentUseCase.ProcessPaymentCommand command = new ProcessPaymentUseCase.ProcessPaymentCommand(
                key,
                source.getId(),
                destination.getId(),
                Money.of("25.00", "USD")
        );

        List<Callable<Object>> tasks = IntStream.range(0, concurrency)
                .<Callable<Object>>mapToObj(i -> () -> {
                    startLatch.await();
                    try {
                        return processPaymentUseCase.processPayment(command);
                    } catch (RuntimeException e) {
                        return e;
                    }
                })
                .collect(Collectors.toList());

        List<Future<Object>> futures = tasks.stream().map(pool::submit).collect(Collectors.toList());
        startLatch.countDown();

        for (Future<Object> f : futures) {
            try {
                f.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException ignored) {
            }
        }
        pool.shutdown();

        long paymentCount = paymentRepository.findAllOrderByCreatedAtDesc().stream()
                .filter(p -> p.getIdempotencyKey().equals(key))
                .count();
        assertThat(paymentCount).isEqualTo(1);

        Account refreshedDestination = manageAccountUseCase.getAccountById(destination.getId());
        assertThat(refreshedDestination.getBalance().getAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
    }
}
