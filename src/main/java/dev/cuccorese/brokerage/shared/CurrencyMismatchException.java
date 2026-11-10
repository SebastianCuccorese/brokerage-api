package dev.cuccorese.brokerage.shared;

import java.util.Currency;

public class CurrencyMismatchException extends IllegalArgumentException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Cannot combine " + expected + " with " + actual);
    }
}
