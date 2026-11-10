package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.market.DailyPrice;
import dev.cuccorese.brokerage.market.DailyPriceRepository;
import dev.cuccorese.brokerage.market.Instrument;
import dev.cuccorese.brokerage.market.InstrumentRepository;
import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.Money;
import dev.cuccorese.brokerage.shared.NotFoundException;
import dev.cuccorese.brokerage.trading.PortfolioView.PositionView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final UserRepository users;
    private final OrderRepository orders;
    private final InstrumentRepository instruments;
    private final DailyPriceRepository dailyPrices;

    @Transactional(readOnly = true)
    public PortfolioView portfolioOf(Integer userId) {
        if (!users.existsById(userId)) {
            throw new NotFoundException("User", userId);
        }
        Ledger ledger = ledgerOf(userId);
        List<Position> open = ledger.openPositions();
        List<Integer> ids = open.stream().map(Position::getInstrumentId).toList();
        Map<Integer, Instrument> instrumentsById = instruments.findAllById(ids).stream()
                .collect(Collectors.toMap(Instrument::getId, Function.identity()));
        Map<Integer, DailyPrice> dailyPricesById = dailyPrices.findLatest(ids).stream()
                .collect(Collectors.toMap(DailyPrice::getInstrumentId, Function.identity()));

        List<PositionView> positions = open.stream()
                .map(position -> view(position, instrumentsById.get(position.getInstrumentId()),
                        dailyPricesById.get(position.getInstrumentId())))
                .sorted(Comparator.comparing(PositionView::ticker))
                .toList();
        Money totalValue = positions.stream()
                .map(PositionView::marketValue)
                .filter(value -> value != null)
                .reduce(ledger.cash(), Money::plus);
        return new PortfolioView(userId, totalValue.rounded(), ledger.availableCash().rounded(), positions);
    }

    /** The ledger the order rules check against; callers decide on locking. */
    Ledger ledgerOf(Integer userId) {
        return Ledger.replay(Currencies.ARS, orders.findByUserIdAndStatusInOrderByDatetimeAscIdAsc(
                userId, EnumSet.of(OrderStatus.FILLED, OrderStatus.NEW)));
    }

    private static PositionView view(Position position, Instrument instrument, DailyPrice dailyPrice) {
        Money marketValue = dailyPrice == null ? null : position.marketValue(dailyPrice.getClose()).rounded();
        BigDecimal totalReturn = dailyPrice == null ? null : percent(position.totalReturn(dailyPrice.getClose()));
        BigDecimal dailyReturn = dailyPrice == null ? null : percent(dailyPrice.dailyReturn());
        return new PositionView(instrument.getId(), instrument.getTicker(), instrument.getName(),
                position.getQuantity(), marketValue, totalReturn, dailyReturn);
    }

    private static BigDecimal percent(BigDecimal fraction) {
        return fraction.movePointRight(2).setScale(2, RoundingMode.HALF_EVEN);
    }
}
