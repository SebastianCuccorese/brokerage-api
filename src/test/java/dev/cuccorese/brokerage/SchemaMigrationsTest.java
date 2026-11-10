package dev.cuccorese.brokerage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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

        assertThat(history).containsExactly("1:BASELINE", "2:SQL", "3:SQL", "4:SQL");
    }

    @Test
    void createsTheIndexesFromV2() {
        List<String> indexes = jdbc.sql("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'")
                .query(String.class).list();

        assertThat(indexes).contains("orders_user_status_idx", "marketdata_instrument_date_idx");
    }

    @Test
    void convertsTheProvidedOrderTimesFromBuenosAiresToUtc() {
        String firstOrder = jdbc.sql("SELECT (datetime AT TIME ZONE 'UTC')::text FROM orders WHERE id = 1")
                .query(String.class).single();

        assertThat(firstOrder).isEqualTo("2023-07-12 15:11:20");
    }
}
