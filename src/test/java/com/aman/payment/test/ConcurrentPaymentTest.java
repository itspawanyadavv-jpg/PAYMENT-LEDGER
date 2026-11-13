package com.aman.payment.test;

import com.aman.payment.application.port.in.ManageAccountUseCase;
import com.aman.payment.application.port.in.ProcessPaymentUseCase;
import com.aman.payment.domain.model.Account;
import com.aman.payment.domain.model.Money;
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

@DisplayName("Concurrent payment correctness")
class ConcurrentPaymentTest extends AbstractIntegrationTest {

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private ManageAccountUseCase manageAccountUseCase;

    @Test
    @DisplayName("Concurrent debits from one account never over-draw or lose updates")
    @Timeout(30)
    void concurrentDebits_noLostUpdates() throws InterruptedException {
        Account source = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Source", "USD", Money.of("1000.00", "USD"));
        Account destination = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "Dest", "USD", Money.of("0.00", "USD"));

        int numPayments = 20;
        Money amountEach = Money.of("10.00", "USD");
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numPayments);

        for (int i = 0; i < numPayments; i++) {
            final String key = "concurrent-debit-" + UUID.randomUUID();
            pool.submit(() -> {
                try {
                    startLatch.await();
                    processPaymentUseCase.processPayment(
                            new ProcessPaymentUseCase.ProcessPaymentCommand(
                                    key,
                                    source.getId(),
                                    destination.getId(),
                                    amountEach
                            ));
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(25, TimeUnit.SECONDS);
        pool.shutdown();

        Account refreshedSource = manageAccountUseCase.getAccountById(source.getId());
        Account refreshedDestination = manageAccountUseCase.getAccountById(destination.getId());

        BigDecimal total = refreshedSource.getBalance().getAmount().add(refreshedDestination.getBalance().getAmount());
        assertThat(total).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(refreshedSource.getBalance().getAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Opposite-direction concurrent payments between the same two accounts do not deadlock")
    @Timeout(30)
    void oppositeDirectionPayments_doNotDeadlock() throws Exception {
        Account accountA = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "A", "USD", Money.of("500.00", "USD"));
        Account accountB = manageAccountUseCase.openAccount(
                "ACC-" + UUID.randomUUID(), "B", "USD", Money.of("500.00", "USD"));

        int roundTrips = 15;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        IntStream.range(0, roundTrips).forEach(i -> {
            futures.add(pool.submit(() -> {
                await(startLatch);
                processPaymentUseCase.processPayment(
                        new ProcessPaymentUseCase.ProcessPaymentCommand(
                                "a-to-b-" + UUID.randomUUID(),
                                accountA.getId(),
                                accountB.getId(),
                                Money.of("5.00", "USD")
                        ));
            }));
            futures.add(pool.submit(() -> {
                await(startLatch);
                processPaymentUseCase.processPayment(
                        new ProcessPaymentUseCase.ProcessPaymentCommand(
                                "b-to-a-" + UUID.randomUUID(),
                                accountB.getId(),
                                accountA.getId(),
                                Money.of("5.00", "USD")
                        ));
            }));
        });

        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get(20, TimeUnit.SECONDS);
        }
        pool.shutdown();

        Account refreshedA = manageAccountUseCase.getAccountById(accountA.getId());
        Account refreshedB = manageAccountUseCase.getAccountById(accountB.getId());
        BigDecimal total = refreshedA.getBalance().getAmount().add(refreshedB.getBalance().getAmount());
        assertThat(total).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
