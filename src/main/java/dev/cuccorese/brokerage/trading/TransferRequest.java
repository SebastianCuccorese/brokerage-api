package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.Money;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** The body of {@code POST /users/{userId}/transfers}, in pesos; {@code amount} fits the {@code NUMERIC(14,2)} column. */
record TransferRequest(
        @NotNull TransferType type,
        @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount) {

    MakeTransfer toMakeTransfer(Integer userId) {
        return new MakeTransfer(userId, type, Money.of(Currencies.ARS, amount));
    }
}
