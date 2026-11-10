package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.market.Instrument;
import dev.cuccorese.brokerage.market.InstrumentType;
import dev.cuccorese.brokerage.shared.ConflictException;
import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.ErrorCode;
import dev.cuccorese.brokerage.shared.InvalidRequestException;
import dev.cuccorese.brokerage.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An order and the rules of its life: {@link #place} decides the status it starts in, and
 * {@link #cancel} is the only transition after that.
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

    private Instant datetime;

    @Column(name = "rejectionreason")
    @Enumerated(EnumType.STRING)
    private RejectionReason rejectionReason;

    private Order(PlaceOrder request, Optional<Money> executionPrice, Instant at) {
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
     * @throws InvalidRequestException when the instrument is the cash currency
     */
    public static Order place(PlaceOrder request, Instrument instrument, Optional<Money> lastClose,
            Ledger ledger, Instant at) {
        if (instrument.getType() == InstrumentType.CURRENCY) {
            throw new InvalidRequestException(ErrorCode.INSTRUMENT_NOT_TRADABLE, instrument.getTicker() + " is cash, not a tradable instrument");
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

    /** @throws ConflictException unless the order is still open ({@code NEW}) */
    public void cancel() {
        if (status != OrderStatus.NEW) {
            throw new ConflictException(ErrorCode.ORDER_NOT_CANCELLABLE,
                    "Order " + id + " is " + status + "; only NEW orders can be cancelled");
        }
        status = OrderStatus.CANCELLED;
    }

    public Money getPrice() {
        return price == null ? null : Money.of(Currencies.ARS, price);
    }
}
