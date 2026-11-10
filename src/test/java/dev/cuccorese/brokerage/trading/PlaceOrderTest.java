package dev.cuccorese.brokerage.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.InvalidRequestException;
import dev.cuccorese.brokerage.shared.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PlaceOrderTest {

    private static Money ars(String amount) {
        return Money.of(Currencies.ARS, new BigDecimal(amount));
    }

    private static PlaceOrder request(OrderSide side, OrderType type, Integer size, Money amount, Money price) {
        return new PlaceOrder(1, 47, side, type, size, amount, price);
    }

    @Test
    void takesExactlyOneOfSizeOrAmount() {
        assertThatThrownBy(() -> request(OrderSide.BUY, OrderType.MARKET, 1, ars("100"), null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> request(OrderSide.BUY, OrderType.MARKET, null, null, null))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void takesAPriceForLimitOrdersOnly() {
        assertThatThrownBy(() -> request(OrderSide.BUY, OrderType.LIMIT, 1, null, null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> request(OrderSide.BUY, OrderType.MARKET, 1, null, ars("100")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void anAmountCountsTheWholeSharesItBuys() {
        assertThat(request(OrderSide.BUY, OrderType.MARKET, null, ars("1000"), null).sharesAt(ars("300")))
                .isEqualTo(3);
        assertThat(request(OrderSide.BUY, OrderType.MARKET, 7, null, null).sharesAt(ars("300")))
                .isEqualTo(7);
    }

    @Test
    void refusesAnAmountThatBuysMoreSharesThanAnOrderHolds() {
        PlaceOrder huge = request(OrderSide.BUY, OrderType.LIMIT, null, ars("999999999999.99"), ars("0.01"));

        assertThatThrownBy(() -> huge.sharesAt(ars("0.01"))).isInstanceOf(InvalidRequestException.class);
    }
}
