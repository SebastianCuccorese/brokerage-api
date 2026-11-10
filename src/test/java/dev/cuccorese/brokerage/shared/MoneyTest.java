package dev.cuccorese.brokerage.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MoneyTest {

    private static final Currency ARS = Currency.getInstance("ARS");
    private static final Currency USD = Currency.getInstance("USD");

    private static Money ars(String amount) {
        return Money.of(ARS, new BigDecimal(amount));
    }

    @Test
    void addsAndSubtractsInTheSameCurrency() {
        assertThat(ars("100.10").plus(ars("0.90")).minus(ars("1"))).isEqualTo(ars("100"));
    }

    @Test
    void refusesToCombineDifferentCurrencies() {
        assertThatThrownBy(() -> ars("1").plus(Money.of(USD, BigDecimal.ONE)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void equalityIgnoresScale() {
        assertThat(ars("10.5")).isEqualTo(ars("10.50")).hasSameHashCodeAs(ars("10.50"));
    }

    @Test
    void multipliesByAQuantity() {
        assertThat(ars("930").times(50)).isEqualTo(ars("46500"));
    }

    @Test
    void countsWholeTimesWithoutFractions() {
        assertThat(ars("1000").wholeTimes(ars("300"))).isEqualTo(3);
        assertThat(ars("299.99").wholeTimes(ars("300"))).isZero();
    }

    @Test
    void roundsHalfEvenToTheCurrencyMinorUnit() {
        assertThat(ars("1.005").rounded().amount()).isEqualByComparingTo("1.00");
        assertThat(ars("1.015").rounded().amount()).isEqualByComparingTo("1.02");
        assertThat(ars("1.005").rounded().amount().scale()).isEqualTo(2);
    }

    @Test
    void comparesAmounts() {
        assertThat(ars("10").isGreaterThan(ars("9.99"))).isTrue();
        assertThat(ars("10").isGreaterThan(ars("10.00"))).isFalse();
    }

    @Test
    void serializesAsCurrencyCodeAndAmount() {
        String json = JsonMapper.builder().build().writeValueAsString(ars("1234.50"));

        assertThat(json).isEqualTo("{\"currency\":\"ARS\",\"amount\":1234.50}");
    }
}
