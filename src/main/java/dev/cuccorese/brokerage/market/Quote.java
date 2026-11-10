package dev.cuccorese.brokerage.market;

import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One day of prices for an instrument; {@code close} is the last traded price. */
@Entity
@Table(name = "marketdata")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quote {

    @Id
    private Integer id;

    @Column(name = "instrumentid")
    private Integer instrumentId;

    private BigDecimal close;

    @Column(name = "previousclose")
    private BigDecimal previousClose;

    private LocalDate date;

    public Money getClose() {
        return Money.of(Currencies.ARS, close);
    }

    public Money getPreviousClose() {
        return Money.of(Currencies.ARS, previousClose);
    }

    /** Change from the previous close as a fraction (0.01 is 1%). */
    public BigDecimal dailyReturn() {
        return close.subtract(previousClose).divide(previousClose, MathContext.DECIMAL64);
    }
}
