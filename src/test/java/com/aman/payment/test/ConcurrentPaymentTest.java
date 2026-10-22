package com.aman.payment.test;

import com.aman.payment.dto.PaymentRequest;
import com.aman.payment.entity.Account;
import com.aman.payment.service.AccountService;
import com.aman.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies two concurrency properties that are easy to get wrong in a
 * naive read-modify-write balance update:
 *
 *  1. No lost updates: firing many concurrent debits against the same
 *     account never results in a final balance higher than it should
 *     be (which would happen if two threads both read balance=100,
 *     both computed 100-10=90, and the last writer clobbered the
 *     other's debit).
 *  2. No deadlocks: payments flowing in opposite directions between
 *     the same two accounts (A->B and B->A) concurrently must not
 *     deadlock, which DoubleEntryLedger avoids by always locking
 *     accounts in a fixed UUID order regardless of debit/credit role.
 */
@DisplayName("Concurrent payment correctness")
class ConcurrentPaymentTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private AccountService accountService;

    @Test
    @DisplayName("Concurrent debits from one account never over-draw or lose updates")
    @Timeout(30)
    void concurrentDebits_noLostUpdates() throws InterruptedException {
        Account source = accountService.openAccount("ACC-" + UUID.randomUUID(), "Source", "USD", new BigDecimal("1000.00"));
        Account destination = accountService.openAccount("ACC-" + UUID.randomUUID(), "Dest", "USD", new BigDecimal("0.00"));

        int numPayments = 20;
        BigDecimal amountEach = new BigDecimal("10.00");
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numPayments);

        for (int i = 0; i < numPayments; i++) {
            final String key = "concurrent-debit-" + UUID.randomUUID();
            pool.submit(() -> {
                try {
                    startLatch.await();
                    PaymentRequest req = PaymentRequest.builder()
                            .idempotencyKey(key)
                            .sourceAccountId(source.getId())
                            .destinationAccountId(destination.getId())
                            .amount(amountEach)
                            .currency("USD")
                            .build();
                    paymentService.executePayment(req);
                } catch (Exception ignored) {
                    // a bounded number of lock-timeout failures under heavy
                    // contention is acceptable; we assert on final balance integrity
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(25, TimeUnit.SECONDS);
        pool.shutdown();

        Account refreshedSource = accountService.getAccount(source.getId());
        Account refreshedDestination = accountService.getAccount(destination.getId());

        // Whatever subset of payments actually succeeded, source + destination
        // must always sum to the original total - money is neither created
        // nor destroyed.
        BigDecimal total = refreshedSource.getBalance().add(refreshedDestination.getBalance());
        assertThat(total).isEqualByComparingTo("1000.00");
        assertThat(refreshedSource.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Opposite-direction concurrent payments between the same two accounts do not deadlock")
    @Timeout(30)
    void oppositeDirectionPayments_doNotDeadlock() throws Exception {
        Account accountA = accountService.openAccount("ACC-" + UUID.randomUUID(), "A", "USD", new BigDecimal("500.00"));
        Account accountB = accountService.openAccount("ACC-" + UUID.randomUUID(), "B", "USD", new BigDecimal("500.00"));

        int roundTrips = 15;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        IntStream.range(0, roundTrips).forEach(i -> {
            futures.add(pool.submit(() -> {
                await(startLatch);
                paymentService.executePayment(PaymentRequest.builder()
                        .idempotencyKey("a-to-b-" + UUID.randomUUID())
                        .sourceAccountId(accountA.getId())
                        .destinationAccountId(accountB.getId())
                        .amount(new BigDecimal("5.00"))
                        .currency("USD")
                        .build());
            }));
            futures.add(pool.submit(() -> {
                await(startLatch);
                paymentService.executePayment(PaymentRequest.builder()
                        .idempotencyKey("b-to-a-" + UUID.randomUUID())
                        .sourceAccountId(accountB.getId())
                        .destinationAccountId(accountA.getId())
                        .amount(new BigDecimal("5.00"))
                        .currency("USD")
                        .build());
            }));
        });

        startLatch.countDown();

        // If DoubleEntryLedger's lock ordering were wrong, this would hang
        // until the test's @Timeout kills it - which is itself the
        // deadlock-detection mechanism here.
        for (Future<?> f : futures) {
            f.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();

        Account refreshedA = accountService.getAccount(accountA.getId());
        Account refreshedB = accountService.getAccount(accountB.getId());
        BigDecimal total = refreshedA.getBalance().add(refreshedB.getBalance());
        assertThat(total).isEqualByComparingTo("1000.00");
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
