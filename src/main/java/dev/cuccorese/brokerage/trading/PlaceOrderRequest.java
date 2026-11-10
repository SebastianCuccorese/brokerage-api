package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.Money;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * The body of {@code POST /orders}, in pesos. {@code price} fits the {@code NUMERIC(10,2)}
 * column; {@code amount} is capped so the shares it buys stay countable in a {@code long}.
 */
record PlaceOrderRequest(
        @NotNull Integer userId,
        @NotNull Integer instrumentId,
        @NotNull OrderSide side,
        @NotNull OrderType type,
        @Positive Integer size,
        @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @Positive @Digits(integer = 8, fraction = 2) BigDecimal price) {

    PlaceOrder toPlaceOrder() {
        return new PlaceOrder(userId, instrumentId, side, type, size, pesos(amount), pesos(price));
    }

    private static Money pesos(BigDecimal amount) {
        return amount == null ? null : Money.of(Currencies.ARS, amount);
    }
}
