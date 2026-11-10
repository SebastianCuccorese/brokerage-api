package dev.cuccorese.brokerage.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.cuccorese.brokerage.market.Instrument;
import dev.cuccorese.brokerage.market.InstrumentType;
import dev.cuccorese.brokerage.shared.ConflictException;
import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.InvalidRequestException;
import dev.cuccorese.brokerage.shared.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final int USER = 1;
    private static final Instrument PESOS = new Instrument(66, "ARS", "PESOS", InstrumentType.CURRENCY);
    private static final Instrument PAMP = new Instrument(47, "PAMP", "Pampa Holding S.A.", InstrumentType.STOCK);
    private static final Instant EARLIER = Instant.parse("2023-07-12T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-10T14:30:00Z");

    private static Money ars(String amount) {
        return Money.of(Currencies.ARS, new BigDecimal(amount));
    }

    private static List<Transfer> deposited(int pesos) {
        return List.of(Transfer.cashIn(USER, ars(String.valueOf(pesos)), EARLIER));
    }

    /** A user who deposited {@code pesos} and holds no shares. */
    private static Ledger cash(int pesos) {
        return Ledger.replay(Currencies.ARS, deposited(pesos), List.of());
    }

    /** The buy of a user who spent all their cash on {@code shares} PAMP at 100. */
    private static Order boughtPamp(int shares) {
        return Order.place(market(OrderSide.BUY, shares), PAMP, Optional.of(ars("100")), cash(shares * 100), EARLIER);
    }

    private static Ledger holding(int shares) {
        return Ledger.replay(Currencies.ARS, deposited(shares * 100), List.of(boughtPamp(shares)));
    }

    private static PlaceOrder market(OrderSide side, int size) {
        return new PlaceOrder(USER, PAMP.getId(), side, OrderType.MARKET, size, null, null);
    }

    private static PlaceOrder limit(OrderSide side, int size, String price) {
        return new PlaceOrder(USER, PAMP.getId(), side, OrderType.LIMIT, size, null, ars(price));
    }

    private static PlaceOrder marketBuyFor(String amount) {
        return new PlaceOrder(USER, PAMP.getId(), OrderSide.BUY, OrderType.MARKET, null, ars(amount), null);
    }

    private static Order place(PlaceOrder request, String lastClose, Ledger ledger) {
        return Order.place(request, PAMP, Optional.ofNullable(lastClose).map(OrderTest::ars), ledger, NOW);
    }

    @Test
    void theHelperLedgersHoldWhatTheySay() {
        assertThat(cash(1000).availableCash()).isEqualTo(ars("1000"));
        assertThat(holding(5).availableCash()).isEqualTo(ars("0"));
        assertThat(holding(5).availableShares(PAMP.getId())).isEqualTo(5);
    }

    @Test
    void aMarketOrderFillsAtTheLastClose() {
        Order order = place(market(OrderSide.BUY, 10), "925.85", cash(10_000));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.getPrice()).isEqualTo(ars("925.85"));
        assertThat(order.getSize()).isEqualTo(10);
        assertThat(order.getDatetime()).isEqualTo(NOW);
        assertThat(order.getRejectionReason()).isNull();
    }

    @Test
    void aLimitOrderStaysOpenAtItsOwnPrice() {
        Order order = place(limit(OrderSide.BUY, 10, "900"), "925.85", cash(10_000));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getPrice()).isEqualTo(ars("900"));
    }

    @Test
    void anAmountBuysOnlyWholeShares() {
        Order order = place(marketBuyFor("1000"), "300", cash(1000));

        assertThat(order.getSize()).isEqualTo(3);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void anAmountBelowOneShareIsRejected() {
        Order order = place(marketBuyFor("299.99"), "300", cash(1000));

        assertThat(order.getSize()).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectionReason()).isEqualTo(RejectionReason.AMOUNT_BELOW_ONE_SHARE);
    }

    @Test
    void aBuyMayUseExactlyTheAvailableCashButNotMore() {
        assertThat(place(market(OrderSide.BUY, 2), "500", cash(1000)).getStatus())
                .isEqualTo(OrderStatus.FILLED);

        Order tooBig = place(market(OrderSide.BUY, 2), "500.01", cash(1000));
        assertThat(tooBig.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(tooBig.getRejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_CASH);
    }

    @Test
    void aSellMayUseExactlyTheAvailableSharesButNotMore() {
        assertThat(place(market(OrderSide.SELL, 5), "925.85", holding(5)).getStatus())
                .isEqualTo(OrderStatus.FILLED);

        Order tooBig = place(limit(OrderSide.SELL, 6, "1000"), "925.85", holding(5));
        assertThat(tooBig.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(tooBig.getRejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_SHARES);
    }

    @Test
    void openSellsCountAgainstTheShares() {
        List<Order> history = new ArrayList<>(List.of(boughtPamp(5)));
        history.add(place(limit(OrderSide.SELL, 3, "1000"), "925.85",
                Ledger.replay(Currencies.ARS, deposited(500), history)));

        assertThat(place(market(OrderSide.SELL, 3), "925.85", Ledger.replay(Currencies.ARS, deposited(500), history))
                .getRejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_SHARES);
    }

    @Test
    void aSellNeedsNoCash() {
        assertThat(place(market(OrderSide.SELL, 1), "925.85", holding(1)).getStatus())
                .isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void aMarketOrderWithoutMarketDataIsRejected() {
        Order order = place(market(OrderSide.BUY, 1), null, cash(10_000));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectionReason()).isEqualTo(RejectionReason.NO_MARKET_DATA);
        assertThat(order.getPrice()).isNull();
        assertThat(order.getSize()).isEqualTo(1);
    }

    @Test
    void aLimitOrderDoesNotNeedMarketData() {
        assertThat(place(limit(OrderSide.BUY, 1, "100"), null, cash(10_000)).getStatus())
                .isEqualTo(OrderStatus.NEW);
    }

    @Test
    void cashIsNotATradableInstrument() {
        PlaceOrder buyPesos = new PlaceOrder(USER, PESOS.getId(), OrderSide.BUY, OrderType.MARKET, 1, null, null);

        assertThatThrownBy(() -> Order.place(buyPesos, PESOS, Optional.of(ars("1")), cash(10_000), NOW))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void anOpenOrderCanBeCancelled() {
        Order order = place(limit(OrderSide.BUY, 1, "100"), null, cash(10_000));

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void onlyAnOpenOrderCanBeCancelled() {
        Order filled = place(market(OrderSide.BUY, 1), "100", cash(10_000));
        Order rejected = place(market(OrderSide.BUY, 1), null, cash(10_000));
        Order cancelled = place(limit(OrderSide.BUY, 1, "100"), null, cash(10_000));
        cancelled.cancel();

        for (Order order : List.of(filled, rejected, cancelled)) {
            OrderStatus before = order.getStatus();
            assertThatThrownBy(order::cancel).isInstanceOf(ConflictException.class);
            assertThat(order.getStatus()).isEqualTo(before);
        }
    }
}
