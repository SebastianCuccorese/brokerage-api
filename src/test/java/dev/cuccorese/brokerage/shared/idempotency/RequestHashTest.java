package dev.cuccorese.brokerage.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RequestHashTest {

    enum Side { BUY, SELL }

    record Trade(Integer userId, Side side, Integer size, Money amount) {
    }

    record OtherTrade(Integer userId, Side side, Integer size, Money amount) {
    }

    record Note(String first, String second) {
    }

    private static Money ars(String amount) {
        return Money.of(Currencies.ARS, new BigDecimal(amount));
    }

    @Test
    void isTheHexSha256OfTheCommand() {
        assertThat(RequestHash.of(new Trade(1, Side.BUY, 10, null)).sha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void theSameCommandHasTheSameHash() {
        assertThat(RequestHash.of(new Trade(1, Side.BUY, null, ars("1000"))))
                .isEqualTo(RequestHash.of(new Trade(1, Side.BUY, null, ars("1000"))));
    }

    @Test
    void amountsCompareByValueNotByScale() {
        assertThat(RequestHash.of(new Trade(1, Side.BUY, null, ars("1000"))))
                .isEqualTo(RequestHash.of(new Trade(1, Side.BUY, null, ars("1000.00"))));
    }

    @Test
    void anyDifferentFieldChangesIt() {
        RequestHash trade = RequestHash.of(new Trade(1, Side.BUY, 10, null));

        assertThat(RequestHash.of(new Trade(2, Side.BUY, 10, null))).isNotEqualTo(trade);
        assertThat(RequestHash.of(new Trade(1, Side.SELL, 10, null))).isNotEqualTo(trade);
        assertThat(RequestHash.of(new Trade(1, Side.BUY, 11, null))).isNotEqualTo(trade);
        assertThat(RequestHash.of(new Trade(1, Side.BUY, 10, ars("0")))).isNotEqualTo(trade);
    }

    @Test
    void theSameFieldsInAnotherCommandDoNotMatch() {
        assertThat(RequestHash.of(new OtherTrade(1, Side.BUY, 10, null)))
                .isNotEqualTo(RequestHash.of(new Trade(1, Side.BUY, 10, null)));
    }

    @Test
    void textCannotShiftIntoTheNextField() {
        assertThat(RequestHash.of(new Note("1,second=2", "3")))
                .isNotEqualTo(RequestHash.of(new Note("1", "2,second=3")));
    }
}
