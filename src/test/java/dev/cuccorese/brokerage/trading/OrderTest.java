package dev.cuccorese.brokerage.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.cuccorese.brokerage.market.Instrument;
import dev.cuccorese.brokerage.market.InstrumentType;
import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final int USER = 1;
    private static final int PAMP = 47;
    private static final Instant NOW = Instant.parse("2026-11-10T14:30:00Z");

    private static Money ars(String amount) {
        return Money.of(Currencies.ARS, new BigDecimal(amount));
    }

    private static Instrument instrument(InstrumentType type) {
        Instrument instrument = mock(Instrument.class);
        when(instrument.getType()).thenReturn(type);
        when(instrument.getTicker()).thenReturn(type == InstrumentType.CURRENCY ? "ARS" : "PAMP");
        return instrument;
    }

    private static Ledger ledger(String availableCash, long availablePampShares) {
        Ledger ledger = mock(Ledger.class);
        when(ledger.availableCash()).thenReturn(ars(availableCash));
        when(ledger.availableShares(PAMP)).thenReturn(availablePampShares);
        return ledger;
    }

    private static PlaceOrder market(OrderSide side, int size) {
        return new PlaceOrder(USER, PAMP, side, OrderType.MARKET, size, null, null);
    }

    private static PlaceOrder limit(OrderSide side, int size, String price) {
        return new PlaceOrder(USER, PAMP, side, OrderType.LIMIT, size, null, ars(price));
    }

    private static PlaceOrder marketBuyFor(String amount) {
        return new PlaceOrder(USER, PAMP, OrderSide.BUY, OrderType.MARKET, null, ars(amount), null);
    }

    private static Order place(PlaceOrder request, String lastClose, Ledger ledger) {
        return Order.place(request, instrument(InstrumentType.STOCK), Optional.ofNullable(lastClose).map(OrderTest::ars),
                ledger, NOW);
    }

    @Test
    void aMarketOrderFillsAtTheLastClose() {
        Order order = place(market(OrderSide.BUY, 10), "925.85", ledger("10000", 0));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.getPrice()).isEqualTo(ars("925.85"));
        assertThat(order.getSize()).isEqualTo(10);
        assertThat(order.getDatetime()).isEqualTo(NOW);
        assertThat(order.getRejectionReason()).isNull();
    }

    @Test
    void aLimitOrderStaysOpenAtItsOwnPrice() {
        Order order = place(limit(OrderSide.BUY, 10, "900"), "925.85", ledger("10000", 0));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getPrice()).isEqualTo(ars("900"));
    }

    @Test
    void anAmountBuysOnlyWholeShares() {
        Order order = place(marketBuyFor("1000"), "300", ledger("1000", 0));

        assertThat(order.getSize()).isEqualTo(3);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void anAmountBelowOneShareIsRejected() {
        Order order = place(marketBuyFor("299.99"), "300", ledger("1000", 0));

        assertThat(order.getSize()).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectionReason()).isEqualTo(RejectionReason.AMOUNT_BELOW_ONE_SHARE);
    }

    @Test
    void aBuyMayUseExactlyTheAvailableCashButNotMore() {
        assertThat(place(market(OrderSide.BUY, 2), "500", ledger("1000", 0)).getStatus())
                .isEqualTo(OrderStatus.FILLED);

        Order tooBig = place(market(OrderSide.BUY, 2), "500.01", ledger("1000", 0));
        assertThat(tooBig.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(tooBig.getRejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_CASH);
    }

    @Test
    void aSellMayUseExactlyTheAvailableSharesButNotMore() {
        assertThat(place(market(OrderSide.SELL, 5), "925.85", ledger("0", 5)).getStatus())
                .isEqualTo(OrderStatus.FILLED);

        Order tooBig = place(limit(OrderSide.SELL, 6, "1000"), "925.85", ledger("0", 5));
        assertThat(tooBig.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(tooBig.getRejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_SHARES);
    }

    @Test
    void aSellNeedsNoCash() {
        assertThat(place(market(OrderSide.SELL, 1), "925.85", ledger("0", 1)).getStatus())
                .isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void aMarketOrderWithoutMarketDataIsRejected() {
        Order order = place(market(OrderSide.BUY, 1), null, ledger("10000", 0));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectionReason()).isEqualTo(RejectionReason.NO_MARKET_DATA);
        assertThat(order.getPrice()).isNull();
        assertThat(order.getSize()).isEqualTo(1);
    }

    @Test
    void aLimitOrderDoesNotNeedMarketData() {
        assertThat(place(limit(OrderSide.BUY, 1, "100"), null, ledger("10000", 0)).getStatus())
                .isEqualTo(OrderStatus.NEW);
    }

    @Test
    void cashIsNotATradableInstrument() {
        assertThatThrownBy(() -> Order.place(market(OrderSide.BUY, 1), instrument(InstrumentType.CURRENCY),
                Optional.of(ars("1")), ledger("10000", 0), NOW))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void anOpenOrderCanBeCancelled() {
        Order order = place(limit(OrderSide.BUY, 1, "100"), null, ledger("10000", 0));

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void onlyAnOpenOrderCanBeCancelled() {
        Order filled = place(market(OrderSide.BUY, 1), "100", ledger("10000", 0));
        Order rejected = place(market(OrderSide.BUY, 1), null, ledger("10000", 0));
        Order cancelled = place(limit(OrderSide.BUY, 1, "100"), null, ledger("10000", 0));
        cancelled.cancel();

        for (Order order : new Order[] {filled, rejected, cancelled}) {
            OrderStatus before = order.getStatus();
            assertThatThrownBy(order::cancel).isInstanceOf(OrderNotCancellableException.class);
            assertThat(order.getStatus()).isEqualTo(before);
        }
    }
}
