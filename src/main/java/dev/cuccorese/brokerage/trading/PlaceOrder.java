package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.ErrorCode;
import dev.cuccorese.brokerage.shared.InvalidRequestException;
import dev.cuccorese.brokerage.shared.Money;
import java.util.EnumSet;

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
        if (!EnumSet.of(OrderSide.BUY, OrderSide.SELL).contains(side)) {
            throw new InvalidRequestException(ErrorCode.SIDE_NOT_TRADABLE, "side must be BUY or SELL; cash moves are not orders");
        }
        if ((size == null) == (amount == null)) {
            throw new InvalidRequestException(ErrorCode.SIZE_OR_AMOUNT_REQUIRED, "Send exactly one of size or amount");
        }
        if ((type == OrderType.LIMIT) != (limitPrice != null)) {
            throw new InvalidRequestException(ErrorCode.LIMIT_PRICE_MISMATCH, "A LIMIT order needs a price and a MARKET order takes none");
        }
    }

    /** Shares the order is for at {@code price}: the size sent, or the whole shares the amount buys. */
    int sharesAt(Money price) {
        if (size != null) {
            return size;
        }
        long shares = amount.wholeTimes(price);
        if (shares > Integer.MAX_VALUE) {
            throw new InvalidRequestException(ErrorCode.ORDER_TOO_LARGE, "The amount buys more shares than one order can hold");
        }
        return (int) shares;
    }
}
