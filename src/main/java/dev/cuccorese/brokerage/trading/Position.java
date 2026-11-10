package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Money;
import java.math.BigDecimal;
import java.util.Currency;
import lombok.Getter;

/**
 * Shares held of one instrument and what they cost, by the average cost method.
 * Quantity and cost are signed: a negative quantity is a short position whose
 * cost is the proceeds of the sale that opened it.
 */
@Getter
public class Position {

    private final Integer instrumentId;
    private long quantity;
    private Money cost;

    Position(Integer instrumentId, Currency currency) {
        this.instrumentId = instrumentId;
        this.cost = Money.zero(currency);
    }

    /** Applies a fill: positive {@code signedSize} buys, negative sells. */
    void apply(long signedSize, Money price) {
        long newQuantity = quantity + signedSize;
        boolean grows = quantity == 0 || Long.signum(quantity) == Long.signum(signedSize);
        boolean crossesZero = !grows && Long.signum(newQuantity) == Long.signum(signedSize);
        if (grows) {
            cost = cost.plus(price.times(signedSize));
        } else if (crossesZero) {
            cost = price.times(newQuantity);
        } else {
            cost = cost.dividedBy(quantity).times(newQuantity);
        }
        quantity = newQuantity;
    }

    public boolean isOpen() {
        return quantity != 0;
    }

    public Money marketValue(Money lastPrice) {
        return lastPrice.times(quantity);
    }

    /** Gain over cost as a fraction (0.01 is 1%), positive when the position made money. */
    public BigDecimal totalReturn(Money lastPrice) {
        return marketValue(lastPrice).minus(cost).ratio(cost).multiply(BigDecimal.valueOf(Long.signum(quantity)));
    }
}
