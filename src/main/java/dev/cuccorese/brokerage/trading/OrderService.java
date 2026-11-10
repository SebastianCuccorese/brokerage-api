package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.market.Instrument;
import dev.cuccorese.brokerage.market.InstrumentRepository;
import dev.cuccorese.brokerage.market.Quote;
import dev.cuccorese.brokerage.market.QuoteRepository;
import dev.cuccorese.brokerage.shared.Money;
import dev.cuccorese.brokerage.shared.NotFoundException;
import java.time.Clock;
import java.time.Instant;
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
    private final QuoteRepository quotes;
    private final OrderRepository orders;
    private final Ledgers ledgers;
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
        Optional<Money> lastClose = quotes.findLatest(List.of(instrument.getId())).stream()
                .findFirst()
                .map(Quote::getClose);
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        return orders.save(Order.place(request, instrument, lastClose, ledgers.of(request.userId()), now));
    }

    /** Locks the order so that two concurrent cancels cannot both see it open. */
    @Transactional
    public Order cancel(Integer orderId) {
        Order order = orders.findWithLockById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
        order.cancel();
        return order;
    }

    @Transactional(readOnly = true)
    public Order get(Integer orderId) {
        return orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }
}
