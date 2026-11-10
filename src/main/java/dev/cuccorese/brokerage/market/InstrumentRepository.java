package dev.cuccorese.brokerage.market;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InstrumentRepository extends JpaRepository<Instrument, Integer> {

    /** Tradable instruments whose ticker or name contains {@code pattern}; the caller escapes LIKE wildcards. */
    @Query("""
            select new dev.cuccorese.brokerage.market.InstrumentView(i.id, i.ticker, i.name, i.type)
            from Instrument i
            where i.type <> dev.cuccorese.brokerage.market.InstrumentType.MONEDA
              and (lower(i.ticker) like lower(:pattern) escape '\\'
                   or lower(i.name) like lower(:pattern) escape '\\')
            order by i.ticker
            """)
    List<InstrumentView> searchTradable(String pattern);
}
