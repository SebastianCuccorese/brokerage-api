package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Money;
import java.math.BigDecimal;
import java.util.List;

/** Percentages are in percent (1.5 means 1.5%); a position without market data has null values. */
public record PortfolioView(Integer userId, Money totalValue, Money availableCash, List<PositionView> positions) {

    public record PositionView(
            Integer instrumentId,
            String ticker,
            String name,
            long quantity,
            Money marketValue,
            BigDecimal totalReturnPct,
            BigDecimal dailyReturnPct) {
    }
}
