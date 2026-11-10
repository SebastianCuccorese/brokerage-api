package dev.cuccorese.brokerage.market;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "instruments")
public class Instrument {

    @Id
    private Integer id;

    private String ticker;

    private String name;

    @Enumerated(EnumType.STRING)
    private InstrumentType type;

    protected Instrument() {
    }

    public Integer id() {
        return id;
    }

    public String ticker() {
        return ticker;
    }

    public String name() {
        return name;
    }

    public InstrumentType type() {
        return type;
    }
}
