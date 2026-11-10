package dev.cuccorese.brokerage.market;

import java.util.Arrays;

/** Instrument kinds; {@code columnValue} is how {@code instruments.type} stores each one. */
public enum InstrumentType {
    STOCK("ACCIONES"),
    CURRENCY("MONEDA");

    private final String columnValue;

    InstrumentType(String columnValue) {
        this.columnValue = columnValue;
    }

    String columnValue() {
        return columnValue;
    }

    static InstrumentType fromColumnValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.columnValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown instrument type in the database: " + value));
    }
}
