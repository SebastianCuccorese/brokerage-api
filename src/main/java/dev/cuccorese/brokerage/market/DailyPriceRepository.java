package dev.cuccorese.brokerage.market;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DailyPriceRepository extends JpaRepository<DailyPrice, Integer> {

    /** The latest day of prices of each instrument; instruments without market data are absent. */
    @Query(nativeQuery = true, value = """
            select distinct on (instrumentid) *
            from marketdata
            where instrumentid in (:instrumentIds)
            order by instrumentid, date desc, id desc
            """)
    List<DailyPrice> findLatest(Collection<Integer> instrumentIds);
}
