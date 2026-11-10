package dev.cuccorese.brokerage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchemaMigrationsTest {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void baselinesTheProvidedSchemaAtOneAndAppliesTheMigrationsOnTop() {
        List<String> history = jdbc.sql("SELECT version || ':' || type FROM flyway_schema_history ORDER BY installed_rank")
                .query(String.class).list();

        assertThat(history).containsExactly("1:BASELINE", "2:SQL", "3:SQL", "4:SQL", "5:SQL");
    }

    @Test
    void createsTheIndexesFromV2() {
        List<String> indexes = jdbc.sql("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'")
                .query(String.class).list();

        assertThat(indexes).contains("orders_user_status_idx", "marketdata_instrument_date_idx");
    }

    @Test
    void convertsTheProvidedOrderTimesFromBuenosAiresToUtc() {
        String firstTrade = jdbc.sql("SELECT (datetime AT TIME ZONE 'UTC')::text FROM orders WHERE id = 2")
                .query(String.class).single();

        assertThat(firstTrade).isEqualTo("2023-07-12 15:31:20");
    }

    @Test
    void movesTheProvidedCashMovementsFromOrdersToTransfersKeepingTheirTimes() {
        List<String> transfers = jdbc.sql("""
                SELECT type || ' ' || amount || ' ' || status || ' ' || (datetime AT TIME ZONE 'UTC')::text
                FROM transfers WHERE userid = 1 ORDER BY id
                """).query(String.class).list();

        assertThat(transfers).containsExactly(
                "DEPOSIT 1000000.00 COMPLETED 2023-07-12 15:11:20",
                "WITHDRAWAL 100000.00 COMPLETED 2023-07-13 15:31:20");
        assertThat(jdbc.sql("SELECT count(*) FROM orders WHERE userid = 1").query(Integer.class).single())
                .isEqualTo(9);
        assertThat(jdbc.sql("SELECT count(*) FROM orders WHERE side NOT IN ('BUY', 'SELL')").query(Integer.class)
                .single()).isZero();
    }

    @Test
    void ordersRefuseCashSidesAfterV5() {
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO orders (instrumentid, userid, size, price, type, side, status, datetime)
                VALUES (66, 1, 1000, 1, 'MARKET', 'CASH_IN', 'FILLED', now())
                """).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("orders_side_check");
    }
}
