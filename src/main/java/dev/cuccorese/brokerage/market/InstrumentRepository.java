package dev.cuccorese.brokerage.market;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InstrumentRepository extends JpaRepository<Instrument, Integer> {

    /**
     * A page of tradable instruments whose ticker or name contains {@code pattern}, ignoring case and accents,
     * ordered by ticker; the caller escapes LIKE wildcards. The expressions must match the indexes of V6
     * exactly, or Postgres cannot use them. Ordering is fixed here, so the pageable must carry no sort.
     */
    @Query("""
            select new dev.cuccorese.brokerage.market.InstrumentView(i.id, i.ticker, i.name, i.type)
            from Instrument i
            where i.type <> dev.cuccorese.brokerage.market.InstrumentType.CURRENCY
              and (immutable_unaccent(lower(i.ticker)) like immutable_unaccent(lower(:pattern)) escape '\\'
                   or immutable_unaccent(lower(i.name)) like immutable_unaccent(lower(:pattern)) escape '\\')
            order by i.ticker
            """)
    Page<InstrumentView> searchTradable(String pattern, Pageable pageable);
}
