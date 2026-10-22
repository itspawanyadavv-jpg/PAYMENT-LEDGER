package com.aman.payment.config;

import com.aman.payment.repository.AccountRepository;
import com.aman.payment.repository.PaymentRepository;
import com.aman.payment.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LedgerHealthIndicator implements HealthIndicator {

    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final ReconciliationService reconciliationService;

    @Override
    public Health health() {
        try {
            long totalAccounts = accountRepository.count();
            long totalPayments = paymentRepository.count();
            List<ReconciliationService.Discrepancy> discrepancies = reconciliationService.reconcileAll();

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
