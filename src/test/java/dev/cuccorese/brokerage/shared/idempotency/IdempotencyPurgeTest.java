package dev.cuccorese.brokerage.shared.idempotency;

import static dev.cuccorese.brokerage.FixedClockConfiguration.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import dev.cuccorese.brokerage.FixedClockConfiguration;
import dev.cuccorese.brokerage.TestcontainersConfiguration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class IdempotencyPurgeTest {

    @Autowired
    private Idempotency idempotency;

    @Autowired
    private JdbcClient jdbc;

    private void insertKey(String key, Instant createdAt) {
        jdbc.sql("""
                insert into idempotency_keys (userid, operation, idempotencykey, requesthash, resourceid, createdat)
                values (1, 'PLACE_ORDER', ?, repeat('0', 64), 1, ?)
                """)
                .params(key, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                .update();
    }

    private boolean exists(String key) {
        return jdbc.sql("select count(*) from idempotency_keys where idempotencykey = ?")
                .param(key).query(Integer.class).single() > 0;
    }

    @Test
    void forgetsKeysOlderThanTheRetentionAndKeepsTheRest() {
        insertKey("purge-expired", NOW.minus(Idempotency.RETENTION).minusSeconds(1));
        insertKey("purge-kept", NOW.minus(Idempotency.RETENTION).plusSeconds(1));

        idempotency.purgeExpired();

        assertThat(exists("purge-expired")).isFalse();
        assertThat(exists("purge-kept")).isTrue();
    }
}
