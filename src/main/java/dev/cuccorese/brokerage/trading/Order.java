package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.market.Instrument;
import dev.cuccorese.brokerage.market.InstrumentType;
import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An order and the rules of its life: {@link #place} decides the status it starts in. The
 * rejection reason is told to the caller but not stored, because the provided schema has no
 * column for it.
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "instrumentid")
    private Integer instrumentId;

    @Column(name = "userid")
    private Integer userId;

    private Integer size;

    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime datetime;

    @Transient
    private RejectionReason rejectionReason;

    private Order(PlaceOrder request, Optional<Money> executionPrice, LocalDateTime at) {
        this.userId = request.userId();
        this.instrumentId = request.instrumentId();
        this.side = request.side();
        this.type = request.type();
        this.price = executionPrice.map(Money::amount).orElse(null);
        this.size = executionPrice.map(request::sharesAt).orElse(request.size());
        this.datetime = at;
    }

    /**
     * Places {@code request} against what the user's {@code ledger} can cover. A MARKET order
     * executes at {@code lastClose} and a LIMIT order is priced at its limit. An order the
     * user cannot cover is still an order: it comes back REJECTED with its reason.
     *
     * @throws InvalidOrderException when the instrument is the cash currency
     */
    public static Order place(PlaceOrder request, Instrument instrument, Optional<Money> lastClose,
            Ledger ledger, LocalDateTime at) {
        if (instrument.getType() == InstrumentType.CURRENCY) {
            throw new InvalidOrderException(instrument.getTicker() + " is cash, not a tradable instrument");
        }
        Optional<Money> executionPrice = switch (request.type()) {
            case MARKET -> lastClose;
            case LIMIT -> Optional.of(request.limitPrice());
        };
        Order order = new Order(request, executionPrice, at);
        order.rejectionReason = order.rejectionAgainst(ledger).orElse(null);
        order.status = order.rejectionReason == null ? order.type.acceptedStatus() : OrderStatus.REJECTED;
        return order;
    }

    private Optional<RejectionReason> rejectionAgainst(Ledger ledger) {
        if (price == null) {
            return Optional.of(RejectionReason.NO_MARKET_DATA);
        }
        if (size == 0) {
            return Optional.of(RejectionReason.AMOUNT_BELOW_ONE_SHARE);
        }
        if (side == OrderSide.BUY && getPrice().times(size).isGreaterThan(ledger.availableCash())) {
            return Optional.of(RejectionReason.INSUFFICIENT_CASH);
        }
        if (side == OrderSide.SELL && size > ledger.availableShares(instrumentId)) {
            return Optional.of(RejectionReason.INSUFFICIENT_SHARES);
        }
        return Optional.empty();
    }

    public Money getPrice() {
        return price == null ? null : Money.of(Currencies.ARS, price);
    }
}
