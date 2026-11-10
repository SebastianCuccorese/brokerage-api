package dev.cuccorese.brokerage.market;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "instruments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Instrument {

    @Id
    private Integer id;

    private String ticker;

    private String name;

    @Convert(converter = InstrumentTypeConverter.class)
    private InstrumentType type;
}
