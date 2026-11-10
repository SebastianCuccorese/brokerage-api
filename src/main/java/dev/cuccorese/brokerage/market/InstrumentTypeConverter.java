package dev.cuccorese.brokerage.market;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
class InstrumentTypeConverter implements AttributeConverter<InstrumentType, String> {

    @Override
    public String convertToDatabaseColumn(InstrumentType type) {
        return type == null ? null : type.columnValue();
    }

    @Override
    public InstrumentType convertToEntityAttribute(String value) {
        return value == null ? null : InstrumentType.fromColumnValue(value);
    }
}
