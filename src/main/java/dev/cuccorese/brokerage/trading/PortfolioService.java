package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.market.Instrument;
import dev.cuccorese.brokerage.market.InstrumentRepository;
import dev.cuccorese.brokerage.market.Quote;
import dev.cuccorese.brokerage.market.QuoteRepository;
import dev.cuccorese.brokerage.shared.ErrorCode;
import dev.cuccorese.brokerage.shared.Money;
import dev.cuccorese.brokerage.shared.NotFoundException;
import dev.cuccorese.brokerage.trading.PortfolioView.PositionView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
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
    private final Ledgers ledgers;
    private final InstrumentRepository instruments;
    private final QuoteRepository quotes;

    @Transactional(readOnly = true)
    public PortfolioView portfolioOf(Integer userId) {
        if (!users.existsById(userId)) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND, "User", userId);
        }
        Ledger ledger = ledgers.of(userId);
        List<Position> open = ledger.openPositions();
        List<Integer> ids = open.stream().map(Position::getInstrumentId).toList();
        Map<Integer, Instrument> instrumentsById = instruments.findAllById(ids).stream()
                .collect(Collectors.toMap(Instrument::getId, Function.identity()));
        Map<Integer, Quote> quotesById = quotes.findLatest(ids).stream()
                .collect(Collectors.toMap(Quote::getInstrumentId, Function.identity()));

        List<PositionView> positions = open.stream()
                .map(position -> view(position, instrumentsById.get(position.getInstrumentId()),
                        quotesById.get(position.getInstrumentId())))
                .sorted(Comparator.comparing(PositionView::ticker))
                .toList();
        Money totalValue = positions.stream()
                .map(PositionView::marketValue)
                .filter(value -> value != null)
                .reduce(ledger.cash(), Money::plus);
        return new PortfolioView(userId, totalValue.rounded(), ledger.availableCash().rounded(), positions);
    }

    private static PositionView view(Position position, Instrument instrument, Quote quote) {
        Money marketValue = quote == null ? null : position.marketValue(quote.getClose()).rounded();
        BigDecimal totalReturn = quote == null ? null : percent(position.totalReturn(quote.getClose()));
        BigDecimal dailyReturn = quote == null ? null : percent(quote.dailyReturn());
        return new PositionView(instrument.getId(), instrument.getTicker(), instrument.getName(),
                position.getQuantity(), marketValue, totalReturn, dailyReturn);
    }

    private static BigDecimal percent(BigDecimal fraction) {
        return fraction.movePointRight(2).setScale(2, RoundingMode.HALF_EVEN);
    }
}
