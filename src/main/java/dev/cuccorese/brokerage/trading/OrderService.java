package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.market.DailyPrice;
import dev.cuccorese.brokerage.market.DailyPriceRepository;
import dev.cuccorese.brokerage.market.Instrument;
import dev.cuccorese.brokerage.market.InstrumentRepository;
import dev.cuccorese.brokerage.shared.Money;
import dev.cuccorese.brokerage.shared.NotFoundException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final UserRepository users;
    private final InstrumentRepository instruments;
    private final DailyPriceRepository dailyPrices;
    private final OrderRepository orders;
    private final Clock clock;

    /**
     * The user's row stays locked until commit, so concurrent orders of the same user are
     * decided one after the other, each against a ledger that includes the previous one.
     */
    @Transactional
    public Order place(PlaceOrder request) {
        users.findWithLockById(request.userId())
                .orElseThrow(() -> new NotFoundException("User", request.userId()));
        Instrument instrument = instruments.findById(request.instrumentId())
                .orElseThrow(() -> new NotFoundException("Instrument", request.instrumentId()));
        Optional<Money> lastClose = dailyPrices.findLatest(List.of(instrument.getId())).stream()
                .findFirst()
                .map(DailyPrice::getClose);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        return orders.save(Order.place(request, instrument, lastClose, orders.ledgerOf(request.userId()), now));
    }

    @Transactional(readOnly = true)
    public Order get(Integer orderId) {
        return orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }
}
