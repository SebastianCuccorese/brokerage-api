package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A user's cash and positions, replayed from their orders in chronological order.
 * Filled orders move cash and shares; new (open) orders only reserve them.
 */
public class Ledger {

    private final Currency currency;
    private Money cash;
    private Money reservedCash;
    private final Map<Integer, Position> positions = new LinkedHashMap<>();
    private final Map<Integer, Long> reservedShares = new HashMap<>();

    private Ledger(Currency currency) {
        this.currency = currency;
        this.cash = Money.zero(currency);
        this.reservedCash = Money.zero(currency);
    }

    public static Ledger replay(Currency currency, List<Order> chronologicalOrders) {
        Ledger ledger = new Ledger(currency);
        chronologicalOrders.forEach(ledger::apply);
        return ledger;
    }

    private void apply(Order order) {
        switch (order.getStatus()) {
            case FILLED -> fill(order);
            case NEW -> reserve(order);
            case REJECTED, CANCELLED -> { }
        }
    }

    /** Cash transfers count their {@code size} as the amount, as the statement asks. */
    private void fill(Order order) {
        long size = order.getSize();
        switch (order.getSide()) {
            case CASH_IN -> cash = cash.plus(Money.of(currency, BigDecimal.valueOf(size)));
            case CASH_OUT -> cash = cash.minus(Money.of(currency, BigDecimal.valueOf(size)));
            case BUY -> {
                cash = cash.minus(order.getPrice().times(size));
                position(order.getInstrumentId()).apply(size, order.getPrice());
            }
            case SELL -> {
                cash = cash.plus(order.getPrice().times(size));
                position(order.getInstrumentId()).apply(-size, order.getPrice());
            }
        }
    }

    private void reserve(Order order) {
        switch (order.getSide()) {
            case BUY -> reservedCash = reservedCash.plus(order.getPrice().times(order.getSize()));
            case SELL -> reservedShares.merge(order.getInstrumentId(), (long) order.getSize(), Long::sum);
            case CASH_IN, CASH_OUT -> { }
        }
    }

    private Position position(Integer instrumentId) {
        return positions.computeIfAbsent(instrumentId, id -> new Position(id, currency));
    }

    public Money cash() {
        return cash;
    }

    /** Cash minus what open buy orders have committed. */
    public Money availableCash() {
        return cash.minus(reservedCash);
    }

    /** Shares held minus what open sell orders have committed. */
    public long availableShares(Integer instrumentId) {
        long held = positions.containsKey(instrumentId) ? positions.get(instrumentId).getQuantity() : 0;
        return held - reservedShares.getOrDefault(instrumentId, 0L);
    }

    public List<Position> openPositions() {
        return positions.values().stream().filter(Position::isOpen).toList();
    }
}
