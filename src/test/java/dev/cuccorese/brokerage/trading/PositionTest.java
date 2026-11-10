package dev.cuccorese.brokerage.trading;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PositionTest {

    private final Position position = new Position(1, Currencies.ARS);

    private static Money ars(String amount) {
        return Money.of(Currencies.ARS, new BigDecimal(amount));
    }

    @Test
    void buysAddToTheCost() {
        position.apply(50, ars("930"));
        position.apply(10, ars("990"));

        assertThat(position.getQuantity()).isEqualTo(60);
        assertThat(position.getCost()).isEqualTo(ars("56400"));
    }

    @Test
    void aPartialSaleKeepsTheAverageCost() {
        position.apply(50, ars("930"));
        position.apply(-10, ars("940"));

        assertThat(position.getQuantity()).isEqualTo(40);
        assertThat(position.getCost()).isEqualTo(ars("37200"));
    }

    @Test
    void closingThePositionClearsTheCostAndReopeningStartsFresh() {
        position.apply(10, ars("100"));
        position.apply(-10, ars("120"));

        assertThat(position.isOpen()).isFalse();
        assertThat(position.getCost()).isEqualTo(ars("0"));

        position.apply(5, ars("200"));
        assertThat(position.getCost()).isEqualTo(ars("1000"));
    }

    @Test
    void sellingMoreThanHeldOpensAShortAtTheSalePrice() {
        position.apply(20, ars("1540"));
        position.apply(-30, ars("1530"));

        assertThat(position.getQuantity()).isEqualTo(-10);
        assertThat(position.getCost()).isEqualTo(ars("-15300"));
    }

    @Test
    void aLongGainsWhenThePriceRises() {
        position.apply(40, ars("930"));

        assertThat(position.marketValue(ars("925.85"))).isEqualTo(ars("37034"));
        assertThat(position.totalReturn(ars("925.85"))).isNegative();
        assertThat(position.totalReturn(ars("1000"))).isPositive();
    }

    @Test
    void aShortGainsWhenThePriceFalls() {
        position.apply(-10, ars("1530"));

        assertThat(position.marketValue(ars("1502.80"))).isEqualTo(ars("-15028"));
        assertThat(position.totalReturn(ars("1502.80"))).isPositive();
        assertThat(position.totalReturn(ars("1600"))).isNegative();
    }
}
