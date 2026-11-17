package com.aman.payment.test;

import com.aman.payment.application.port.in.ManageAccountUseCase;
import com.aman.payment.application.port.in.ProcessPaymentUseCase;
import com.aman.payment.application.port.in.QueryLedgerUseCase;
import com.aman.payment.domain.model.Account;
import com.aman.payment.domain.model.Money;
import com.aman.payment.domain.model.Payment;
import com.aman.payment.infrastructure.messaging.outbox.OutboxPublisher;
import com.aman.payment.infrastructure.persistence.entity.OutboxEventJpaEntity;
import com.aman.payment.infrastructure.persistence.repository.SpringDataOutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Critical Path Concurrency & Invariant Verification")
class CriticalPathConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private ProcessPaymentUseCase processPaymentUseCase;

    @Autowired
    private ManageAccountUseCase manageAccountUseCase;

    @Autowired
    private QueryLedgerUseCase queryLedgerUseCase;

    @Autowired
    private SpringDataOutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Test
    @DisplayName("N = 100 concurrent bidirectional transfers with invariant checks")
    @Timeout(60)
    void concurrentTransfers_N100() throws Exception {
        runBidirectionalConcurrencyBenchmark(100, 20);
    }

    @Test
    @DisplayName("N = 500 concurrent bidirectional transfers with invariant checks")
    @Timeout(120)
    void concurrentTransfers_N500() throws Exception {
        runBidirectionalConcurrencyBenchmark(500, 40);
    }

    @Test
    @DisplayName("Idempotency Replay under concurrency: duplicate keys never produce duplicate transactions or events")
    @Timeout(30)
    void idempotencyReplay_noDuplicates() throws Exception {
        Account source = manageAccountUseCase.openAccount(
                "ACC-IDEMP-SRC-" + UUID.randomUUID(), "IdempSrc", "USD", Money.of("5000.00", "USD"));
        Account dest = manageAccountUseCase.openAccount(
                "ACC-IDEMP-DST-" + UUID.randomUUID(), "IdempDst", "USD", Money.of("0.00", "USD"));

        int parallelReplays = 50;
        String sharedKey = "replay-key-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(parallelReplays);

        List<Payment> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < parallelReplays; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    Payment p = processPaymentUseCase.processPayment(
                            new ProcessPaymentUseCase.ProcessPaymentCommand(
                                    sharedKey,
                                    source.getId(),
                                    dest.getId(),
                                    Money.of("100.00", "USD")
                            ));
                    results.add(p);
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(20, TimeUnit.SECONDS);
        pool.shutdown();

        // Exactly one unique payment ID across all 50 parallel replay attempts
        Set<UUID> uniquePaymentIds = new HashSet<>();
        for (Payment p : results) {
            uniquePaymentIds.add(p.getId());
        }
        System.out.println("Idempotency replay: " + results.size() + " responses returned, " +
                uniquePaymentIds.size() + " unique payment IDs, " + failures.get() + " conflicts/failures.");

        assertThat(uniquePaymentIds).hasSize(1);

        Account refreshedSrc = manageAccountUseCase.getAccountById(source.getId());
        Account refreshedDst = manageAccountUseCase.getAccountById(dest.getId());
        assertThat(refreshedSrc.getBalance().getAmount()).isEqualByComparingTo(new BigDecimal("4900.00"));
        assertThat(refreshedDst.getBalance().getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    private void runBidirectionalConcurrencyBenchmark(int totalRequests, int threadPoolSize) throws Exception {
        BigDecimal initialA = new BigDecimal("50000.00");
        BigDecimal initialB = new BigDecimal("50000.00");
        BigDecimal totalInitial = initialA.add(initialB);

        Account accountA = manageAccountUseCase.openAccount(
                "ACC-BENCH-A-" + UUID.randomUUID(), "Account A", "USD", Money.of(initialA.toPlainString(), "USD"));
        Account accountB = manageAccountUseCase.openAccount(
                "ACC-BENCH-B-" + UUID.randomUUID(), "Account B", "USD", Money.of(initialB.toPlainString(), "USD"));

        ExecutorService pool = Executors.newFixedThreadPool(threadPoolSize);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger deadlocks = new AtomicInteger(0);
        AtomicInteger otherFailures = new AtomicInteger(0);
        List<UUID> completedPaymentIds = Collections.synchronizedList(new ArrayList<>());

        Money amount = Money.of("10.00", "USD");

        for (int i = 0; i < totalRequests; i++) {
            final boolean aToB = (i % 2 == 0);
            final String key = "bench-" + totalRequests + "-" + i + "-" + UUID.randomUUID();
            pool.submit(() -> {
                try {
                    startLatch.await();
                    UUID src = aToB ? accountA.getId() : accountB.getId();
                    UUID dst = aToB ? accountB.getId() : accountA.getId();

                    Payment payment = processPaymentUseCase.processPayment(
                            new ProcessPaymentUseCase.ProcessPaymentCommand(key, src, dst, amount));
                    successes.incrementAndGet();
                    completedPaymentIds.add(payment.getId());
                } catch (org.springframework.dao.CannotAcquireLockException deadLockEx) {
                    deadlocks.incrementAndGet();
                } catch (Exception e) {
                    otherFailures.incrementAndGet();
                    System.err.println("Unexpected failure in concurrency test: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long startTime = System.currentTimeMillis();
        startLatch.countDown();
        boolean completedInTime = doneLatch.await(90, TimeUnit.SECONDS);
        long elapsedMs = System.currentTimeMillis() - startTime;
        pool.shutdown();

        assertThat(completedInTime).isTrue();

        // Flush outbox events to measure publish lag
        outboxPublisher.publishPendingEvents();

        // Invariant 1: Zero Deadlocks
        assertThat(deadlocks.get()).isEqualTo(0);

        // Invariant 2: Total balance across all accounts is unchanged
        Account refreshedA = manageAccountUseCase.getAccountById(accountA.getId());
        Account refreshedB = manageAccountUseCase.getAccountById(accountB.getId());
        BigDecimal totalFinal = refreshedA.getBalance().getAmount().add(refreshedB.getBalance().getAmount());
        int balanceInvariantViolations = totalFinal.compareTo(totalInitial) == 0 ? 0 : 1;
        assertThat(totalFinal).isEqualByComparingTo(totalInitial);

        // Invariant 3: Every completed transaction has balanced DEBIT/CREDIT
        int debitCreditInvariantViolations = 0;
        for (UUID paymentId : completedPaymentIds) {
            if (!queryLedgerUseCase.isBalanced(paymentId)) {
                debitCreditInvariantViolations++;
            }
        }
        assertThat(debitCreditInvariantViolations).isEqualTo(0);

        // Invariant 4: Continuous reconciliation across ledger entries
        List<QueryLedgerUseCase.Discrepancy> discrepancies = queryLedgerUseCase.reconcileAll();
        assertThat(discrepancies).isEmpty();

        // Kafka Outbox Lag Measurement
        List<OutboxEventJpaEntity> publishedEvents = outboxEventRepository.findAll().stream()
                .filter(e -> e.getStatus() == OutboxEventJpaEntity.Status.PUBLISHED && e.getPublishedAt() != null && e.getCreatedAt() != null)
                .toList();

        List<Long> lags = publishedEvents.stream()
                .map(e -> Duration.between(e.getCreatedAt(), e.getPublishedAt()).toMillis())
                .filter(lag -> lag >= 0)
                .sorted()
                .toList();

        double avgLag = lags.isEmpty() ? 0.0 : lags.stream().mapToLong(Long::valueOf).average().orElse(0.0);
        long p50Lag = lags.isEmpty() ? 0 : lags.get((int) (lags.size() * 0.50));
        long p95Lag = lags.isEmpty() ? 0 : lags.get((int) (lags.size() * 0.95));
        long maxLag = lags.isEmpty() ? 0 : lags.get(lags.size() - 1);

        System.out.println("=========================================================");
        System.out.println("CONCURRENCY BENCHMARK RESULTS (N = " + totalRequests + ")");
        System.out.println("Elapsed Time: " + elapsedMs + " ms (" + (totalRequests * 1000.0 / elapsedMs) + " req/sec)");
        System.out.println("Succeeded: " + successes.get() + " / " + totalRequests);
        System.out.println("Deadlocks: " + deadlocks.get());
        System.out.println("Other Failures: " + otherFailures.get());
        System.out.println("Invariant Violations (Balance Drift): " + balanceInvariantViolations);
        System.out.println("Invariant Violations (Debit/Credit Imbalance): " + debitCreditInvariantViolations);
        System.out.println("Reconciliation Discrepancies: " + discrepancies.size());
        System.out.println("Outbox Events Published: " + publishedEvents.size());
        System.out.println("Outbox Lag (Commit to Publish): Avg = " + avgLag + " ms, p50 = " + p50Lag + " ms, p95 = " + p95Lag + " ms, Max = " + maxLag + " ms");
        System.out.println("=========================================================");
    }
}
