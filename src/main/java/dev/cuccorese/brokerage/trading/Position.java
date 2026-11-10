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

    /**
     * Applies a fill: positive {@code signedSize} buys, negative sells. A trade first
     * closes what it can of the current position at the average cost, and whatever
     * is left opens or extends the position at the trade price.
     */
    void apply(long signedSize, Money price) {
        long closing = closingPart(signedSize);
        reduce(closing);
        extend(signedSize - closing, price);
    }

    private long closingPart(long signedSize) {
        boolean opposesPosition = Long.signum(signedSize) == -Long.signum(quantity);
        return opposesPosition ? Long.signum(signedSize) * Math.min(Math.abs(signedSize), Math.abs(quantity)) : 0;
    }

    private void reduce(long size) {
        if (size == 0) {
            return;
        }
        Money averageCost = cost.dividedBy(quantity);
        quantity += size;
        cost = averageCost.times(quantity);
    }

    private void extend(long size, Money price) {
        quantity += size;
        cost = cost.plus(price.times(size));
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
