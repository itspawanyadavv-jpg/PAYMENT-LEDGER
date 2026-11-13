package com.aman.payment.infrastructure.config;

import com.aman.payment.application.port.in.QueryLedgerUseCase;
import com.aman.payment.application.port.out.AccountRepositoryPort;
import com.aman.payment.application.port.out.PaymentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LedgerHealthIndicator implements HealthIndicator {

    private final AccountRepositoryPort accountRepository;
    private final PaymentRepositoryPort paymentRepository;
    private final QueryLedgerUseCase queryLedgerUseCase;

    @Override
    public Health health() {
        try {
            long totalAccounts = accountRepository.findAll().size();
            long totalPayments = paymentRepository.count();
            List<QueryLedgerUseCase.Discrepancy> discrepancies = queryLedgerUseCase.reconcileAll();

            if (!discrepancies.isEmpty()) {
                return Health.down()
                        .withDetail("reconciliation", "DISCREPANCIES_DETECTED")
                        .withDetail("discrepancyCount", discrepancies.size())
                        .withDetail("discrepancies", discrepancies)
                        .build();
            }

            return Health.up()
                    .withDetail("ledgerInvariants", "BALANCED")
                    .withDetail("totalAccounts", totalAccounts)
                    .withDetail("totalPayments", totalPayments)
                    .withDetail("discrepancyCount", 0)
                    .build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
