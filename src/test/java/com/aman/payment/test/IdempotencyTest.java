package com.aman.payment.test;

import com.aman.payment.dto.AccountResponse;
import com.aman.payment.dto.PaymentRequest;
import com.aman.payment.dto.PaymentResponse;
import com.aman.payment.entity.Account;
import com.aman.payment.exception.DuplicatePaymentException;
import com.aman.payment.repository.PaymentRepository;
import com.aman.payment.service.AccountService;
import com.aman.payment.service.PaymentService;
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

/**
 * Verifies the idempotency guarantee: retrying the exact same payment
 * request (same idempotency key, same payload) must never move money
 * twice, must return the same payment id every time, and reusing a key
 * with a DIFFERENT payload must be rejected as a client error rather
 * than silently processed.
 */
@DisplayName("Idempotency behaviour")
class IdempotencyTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private PaymentRepository paymentRepository;

    private Account source;
    private Account destination;

    @BeforeEach
    void setUp() {
        source = accountService.openAccount("ACC-" + UUID.randomUUID(), "Alice", "USD", new BigDecimal("1000.00"));
        destination = accountService.openAccount("ACC-" + UUID.randomUUID(), "Bob", "USD", new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Retrying with the same idempotency key returns the same result and does not double-move money")
    void sameKeySameRequest_isSafeToRetry() {
        String key = "idem-" + UUID.randomUUID();
        PaymentRequest request = PaymentRequest.builder()
                .idempotencyKey(key)
                .sourceAccountId(source.getId())
                .destinationAccountId(destination.getId())
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .build();

        PaymentResponse first = paymentService.executePayment(request);
        PaymentResponse second = paymentService.executePayment(request);

        assertThat(second.getPaymentId()).isEqualTo(first.getPaymentId());
        assertThat(paymentRepository.count()).isEqualTo(1);

        Account refreshedDestination = accountService.getAccount(destination.getId());
        assertThat(refreshedDestination.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Reusing an idempotency key with a different payload is rejected")
    void sameKeyDifferentRequest_isRejected() {
        String key = "idem-" + UUID.randomUUID();
        PaymentRequest first = PaymentRequest.builder()
                .idempotencyKey(key)
                .sourceAccountId(source.getId())
                .destinationAccountId(destination.getId())
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .build();

        paymentService.executePayment(first);

        PaymentRequest conflicting = PaymentRequest.builder()
                .idempotencyKey(key)
                .sourceAccountId(source.getId())
                .destinationAccountId(destination.getId())
                .amount(new BigDecimal("999.00")) // different amount, same key
                .currency("USD")
                .build();

        assertThatThrownBy(() -> paymentService.executePayment(conflicting))
                .isInstanceOf(DuplicatePaymentException.class);
    }

    @Test
    @DisplayName("N concurrent requests with the same idempotency key result in exactly one payment")
    void concurrentRequestsSameKey_resultInExactlyOnePayment() throws InterruptedException {
        String key = "idem-" + UUID.randomUUID();
        int concurrency = 10;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);

        PaymentRequest request = PaymentRequest.builder()
                .idempotencyKey(key)
                .sourceAccountId(source.getId())
                .destinationAccountId(destination.getId())
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .build();

        List<Callable<Object>> tasks = IntStream.range(0, concurrency)
                .<Callable<Object>>mapToObj(i -> () -> {
                    startLatch.await();
                    try {
                        return paymentService.executePayment(request);
                    } catch (RuntimeException e) {
                        return e; // in-flight conflicts are an acceptable outcome for losers of the race
                    }
                })
                .collect(Collectors.toList());

        List<Future<Object>> futures = tasks.stream().map(pool::submit).collect(Collectors.toList());
        startLatch.countDown();

        for (Future<Object> f : futures) {
            try {
                f.get(10, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException ignored) {
                // acceptable: contended attempts may fail/timeout, we only assert on final state
            }
        }
        pool.shutdown();

        long paymentCount = paymentRepository.findAll().stream()
                .filter(p -> p.getIdempotencyKey().equals(key))
                .count();
        assertThat(paymentCount).isEqualTo(1);

        Account refreshedDestination = accountService.getAccount(destination.getId());
        assertThat(refreshedDestination.getBalance()).isEqualByComparingTo("25.00");
    }
}
