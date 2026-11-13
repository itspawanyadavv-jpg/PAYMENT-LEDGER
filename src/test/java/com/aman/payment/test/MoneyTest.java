package com.aman.payment.test;

import com.aman.payment.domain.exception.CurrencyMismatchException;
import com.aman.payment.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Money Value Object Specification")
class MoneyTest {

    @Test
    @DisplayName("Should enforce 4 decimal places with banker's rounding")
    void shouldEnforceScaleAndRounding() {
        Money money = Money.of(new BigDecimal("100.12345"), "USD");
        // Banker's rounding HALF_EVEN on 5 after 4 (even) rounds to 4
        assertThat(money.getAmount()).isEqualByComparingTo(new BigDecimal("100.1234"));

        Money money2 = Money.of(new BigDecimal("100.12355"), "USD");
        // Banker's rounding HALF_EVEN on 5 after 5 (odd) rounds up to 6
        assertThat(money2.getAmount()).isEqualByComparingTo(new BigDecimal("100.1236"));
    }

    @Test
    @DisplayName("Should correctly perform arithmetic with identical currency")
    void shouldPerformArithmetic() {
        Money m1 = Money.of("100.00", "USD");
        Money m2 = Money.of("50.00", "USD");

        Money sum = m1.add(m2);
        assertThat(sum.getAmount()).isEqualByComparingTo("150.00");
        assertThat(sum.getCurrencyCode()).isEqualTo("USD");

        Money diff = m1.subtract(m2);
        assertThat(diff.getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Should reject arithmetic across different currencies")
    void shouldRejectCurrencyMismatch() {
        Money usd = Money.of("100.00", "USD");
        Money eur = Money.of("50.00", "EUR");

        assertThatThrownBy(() -> usd.add(eur))
                .isInstanceOf(CurrencyMismatchException.class);

        assertThatThrownBy(() -> usd.subtract(eur))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    @DisplayName("Should compare monetary values correctly")
    void shouldCompareValues() {
        Money low = Money.of("10.00", "USD");
        Money high = Money.of("20.00", "USD");
        Money equalLow = Money.of("10.0000", "USD");

        assertThat(high.isGreaterThan(low)).isTrue();
        assertThat(low.isLessThan(high)).isTrue();
        assertThat(low.equals(equalLow)).isTrue();
        assertThat(low.hashCode()).isEqualTo(equalLow.hashCode());
    }
}
