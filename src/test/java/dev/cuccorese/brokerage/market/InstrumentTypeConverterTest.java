package dev.cuccorese.brokerage.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InstrumentTypeConverterTest {

    private final InstrumentTypeConverter converter = new InstrumentTypeConverter();

    @Test
    void mapsTheStoredValuesBothWays() {
        assertThat(converter.convertToEntityAttribute("ACCIONES")).isEqualTo(InstrumentType.STOCK);
        assertThat(converter.convertToEntityAttribute("MONEDA")).isEqualTo(InstrumentType.CURRENCY);
        assertThat(converter.convertToDatabaseColumn(InstrumentType.STOCK)).isEqualTo("ACCIONES");
    }

    @Test
    void failsClearlyOnAnUnknownValue() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("BONOS"))
                .hasMessageContaining("BONOS");
    }
}
