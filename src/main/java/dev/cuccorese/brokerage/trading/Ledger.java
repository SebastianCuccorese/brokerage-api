package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Money;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A user's cash and positions. Completed transfers move cash, and orders are replayed in
 * chronological order: filled ones move cash and shares, new (open) ones only reserve them.
 * Transfers need no place in that chronology because they only add to or take from the cash.
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

    public static Ledger replay(Currency currency, List<Transfer> transfers, List<Order> chronologicalOrders) {
        Ledger ledger = new Ledger(currency);
        transfers.forEach(ledger::apply);
        chronologicalOrders.forEach(ledger::apply);
        return ledger;
    }

    private void apply(Transfer transfer) {
        switch (transfer.getStatus()) {
            case COMPLETED -> cash = switch (transfer.getType()) {
                case DEPOSIT -> cash.plus(transfer.getAmount());
                case WITHDRAWAL -> cash.minus(transfer.getAmount());
            };
            case REJECTED -> { }
        }
    }

    private void apply(Order order) {
        switch (order.getStatus()) {
            case FILLED -> fill(order);
            case NEW -> reserve(order);
            case REJECTED, CANCELLED -> { }
        }
    }

    private void fill(Order order) {
        long size = order.getSize();
        switch (order.getSide()) {
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
