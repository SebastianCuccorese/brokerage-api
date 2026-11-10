package dev.cuccorese.brokerage.shared;

import java.util.Currency;

/** The provided schema stores every price and cash amount in pesos, without a currency column. */
public final class Currencies {

    public static final Currency ARS = Currency.getInstance("ARS");

    private Currencies() {
    }
}
