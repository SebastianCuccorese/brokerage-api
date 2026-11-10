package dev.cuccorese.brokerage.shared;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount in a currency. Arithmetic keeps full precision and only combines
 * amounts of the same currency; rounding happens once, when presenting.
 */
public record Money(Currency currency, BigDecimal amount) {

    public Money {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
    }

    public static Money of(Currency currency, BigDecimal amount) {
        return new Money(currency, amount);
    }

    public static Money zero(Currency currency) {
        return new Money(currency, BigDecimal.ZERO);
    }

    public Money plus(Money other) {
        return new Money(currency, amount.add(sameCurrency(other).amount));
    }

    public Money minus(Money other) {
        return new Money(currency, amount.subtract(sameCurrency(other).amount));
    }

    public Money times(long quantity) {
        return new Money(currency, amount.multiply(BigDecimal.valueOf(quantity)));
    }

    /** How many whole times {@code divisor} fits in this amount, e.g. shares affordable at a price. */
    public long wholeTimes(Money divisor) {
        return amount.divideToIntegralValue(sameCurrency(divisor).amount).longValueExact();
    }

    public BigDecimal ratio(Money divisor) {
        return amount.divide(sameCurrency(divisor).amount, MathContext.DECIMAL64);
    }

    public boolean isGreaterThan(Money other) {
        return amount.compareTo(sameCurrency(other).amount) > 0;
    }

    /** Rounded to the currency's minor unit (2 decimals for ARS), half-even. */
    public Money rounded() {
        return new Money(currency, amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN));
    }

    private Money sameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
        return other;
    }

    /** Equal when the values are equal: 10.5 ARS equals 10.50 ARS, unlike {@link BigDecimal#equals}. */
    @Override
    public boolean equals(Object o) {
        return o instanceof Money other
                && currency.equals(other.currency)
                && amount.compareTo(other.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(currency, amount.stripTrailingZeros());
    }
}
