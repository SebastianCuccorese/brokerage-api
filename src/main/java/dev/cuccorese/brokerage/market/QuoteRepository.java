package dev.cuccorese.brokerage.market;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuoteRepository extends JpaRepository<Quote, Integer> {

    /** The most recent quote of each instrument; instruments without market data are absent. */
    @Query(nativeQuery = true, value = """
            select distinct on (instrumentid) *
            from marketdata
            where instrumentid in (:instrumentIds)
            order by instrumentid, date desc, id desc
            """)
    List<Quote> findLatest(Collection<Integer> instrumentIds);
}
