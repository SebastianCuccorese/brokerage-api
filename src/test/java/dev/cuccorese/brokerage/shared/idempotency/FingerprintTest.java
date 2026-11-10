package dev.cuccorese.brokerage.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FingerprintTest {

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
        assertThat(Fingerprint.of(new Trade(1, Side.BUY, 10, null)).sha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void theSameCommandHasTheSameFingerprint() {
        assertThat(Fingerprint.of(new Trade(1, Side.BUY, null, ars("1000"))))
                .isEqualTo(Fingerprint.of(new Trade(1, Side.BUY, null, ars("1000"))));
    }

    @Test
    void amountsCompareByValueNotByScale() {
        assertThat(Fingerprint.of(new Trade(1, Side.BUY, null, ars("1000"))))
                .isEqualTo(Fingerprint.of(new Trade(1, Side.BUY, null, ars("1000.00"))));
    }

    @Test
    void anyDifferentFieldChangesIt() {
        Fingerprint trade = Fingerprint.of(new Trade(1, Side.BUY, 10, null));

        assertThat(Fingerprint.of(new Trade(2, Side.BUY, 10, null))).isNotEqualTo(trade);
        assertThat(Fingerprint.of(new Trade(1, Side.SELL, 10, null))).isNotEqualTo(trade);
        assertThat(Fingerprint.of(new Trade(1, Side.BUY, 11, null))).isNotEqualTo(trade);
        assertThat(Fingerprint.of(new Trade(1, Side.BUY, 10, ars("0")))).isNotEqualTo(trade);
    }

    @Test
    void theSameFieldsInAnotherCommandDoNotMatch() {
        assertThat(Fingerprint.of(new OtherTrade(1, Side.BUY, 10, null)))
                .isNotEqualTo(Fingerprint.of(new Trade(1, Side.BUY, 10, null)));
    }

    @Test
    void textCannotShiftIntoTheNextField() {
        assertThat(Fingerprint.of(new Note("1,second=2", "3")))
                .isNotEqualTo(Fingerprint.of(new Note("1", "2,second=3")));
    }
}
