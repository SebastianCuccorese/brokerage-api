package dev.cuccorese.brokerage.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.cuccorese.brokerage.market.Instrument;
import dev.cuccorese.brokerage.market.InstrumentType;
import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.ErrorCode;
import dev.cuccorese.brokerage.shared.InvalidRequestException;
import dev.cuccorese.brokerage.shared.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TransferTest {

    private static final int USER = 1;
    private static final Instrument PAMP = new Instrument(47, "PAMP", "Pampa Holding S.A.", InstrumentType.STOCK);
    private static final Instant EARLIER = Instant.parse("2023-07-12T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-10T14:30:00Z");

    private static Money ars(String amount) {
        return Money.of(Currencies.ARS, new BigDecimal(amount));
    }

    private static Ledger ledger(List<Transfer> transfers, List<Order> orders) {
        return Ledger.replay(Currencies.ARS, transfers, orders);
    }

    private static Ledger cash(String pesos) {
        return ledger(List.of(Transfer.cashIn(USER, ars(pesos), EARLIER)), List.of());
    }

    @Test
    void aDepositIsCompletedAndAddsToTheCash() {
        Transfer deposit = Transfer.cashIn(USER, ars("1000.50"), NOW);

        assertThat(deposit.getType()).isEqualTo(TransferType.CASH_IN);
        assertThat(deposit.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(deposit.getAmount()).isEqualTo(ars("1000.50"));
        assertThat(deposit.getDatetime()).isEqualTo(NOW);
        assertThat(deposit.getRejectionReason()).isNull();
        assertThat(ledger(List.of(deposit), List.of()).availableCash()).isEqualTo(ars("1000.50"));
    }

    @Test
    void aWithdrawalMayTakeExactlyTheAvailableCashButNotMore() {
        Transfer all = Transfer.cashOut(USER, ars("1000"), cash("1000"), NOW);
        assertThat(all.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(all.getRejectionReason()).isNull();

        Transfer tooMuch = Transfer.cashOut(USER, ars("1000.01"), cash("1000"), NOW);
        assertThat(tooMuch.getType()).isEqualTo(TransferType.CASH_OUT);
        assertThat(tooMuch.getStatus()).isEqualTo(TransferStatus.REJECTED);
        assertThat(tooMuch.getRejectionReason()).isEqualTo(TransferRejectionReason.INSUFFICIENT_CASH);
    }

    @Test
    void openLimitBuysKeepTheCashTheyReserve() {
        List<Transfer> deposits = List.of(Transfer.cashIn(USER, ars("1000"), EARLIER));
        Order openBuy = Order.place(new PlaceOrder(USER, PAMP.getId(), OrderSide.BUY, OrderType.LIMIT, 1, null,
                ars("600")), PAMP, Optional.empty(), ledger(deposits, List.of()), EARLIER);
        Ledger reserved = ledger(deposits, List.of(openBuy));

        assertThat(Transfer.cashOut(USER, ars("400"), reserved, NOW).getStatus())
                .isEqualTo(TransferStatus.COMPLETED);
        assertThat(Transfer.cashOut(USER, ars("400.01"), reserved, NOW).getRejectionReason())
                .isEqualTo(TransferRejectionReason.INSUFFICIENT_CASH);
    }

    @Test
    void onlyCompletedWithdrawalsTakeCash() {
        Transfer deposit = Transfer.cashIn(USER, ars("1000"), EARLIER);
        Transfer completed = Transfer.cashOut(USER, ars("300"), cash("1000"), NOW);
        Transfer rejected = Transfer.cashOut(USER, ars("5000"), cash("1000"), NOW);

        Ledger ledger = ledger(List.of(deposit, completed, rejected), List.of());

        assertThat(ledger.cash()).isEqualTo(ars("700"));
    }

    @Test
    void anAmountMustBePositive() {
        assertThatThrownBy(() -> Transfer.cashIn(USER, ars("0"), NOW))
                .isInstanceOf(InvalidRequestException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.TRANSFER_AMOUNT_NOT_POSITIVE);
        assertThatThrownBy(() -> Transfer.cashOut(USER, ars("-1"), cash("1000"), NOW))
                .isInstanceOf(InvalidRequestException.class);
    }
}
