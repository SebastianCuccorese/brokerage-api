package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Money;

/**
 * What a user asks to trade: a BUY or SELL for exactly one of {@code size} (shares) or
 * {@code amount} (money to invest), with a {@code limitPrice} for LIMIT orders only.
 */
public record PlaceOrder(
        Integer userId,
        Integer instrumentId,
        OrderSide side,
        OrderType type,
        Integer size,
        Money amount,
        Money limitPrice) {

    public PlaceOrder {
        if ((size == null) == (amount == null)) {
            throw new InvalidOrderException("Send exactly one of size or amount");
        }
        if ((type == OrderType.LIMIT) != (limitPrice != null)) {
            throw new InvalidOrderException("A LIMIT order needs a price and a MARKET order takes none");
        }
    }

    /** Shares the order is for at {@code price}: the size sent, or the whole shares the amount buys. */
    int sharesAt(Money price) {
        if (size != null) {
            return size;
        }
        long shares = amount.wholeTimes(price);
        if (shares > Integer.MAX_VALUE) {
            throw new InvalidOrderException("The amount buys more shares than one order can hold");
        }
        return (int) shares;
    }
}
