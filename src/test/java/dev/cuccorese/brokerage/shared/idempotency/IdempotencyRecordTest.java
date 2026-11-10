package dev.cuccorese.brokerage.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.cuccorese.brokerage.shared.DomainException;
import dev.cuccorese.brokerage.shared.ErrorCode;
import dev.cuccorese.brokerage.shared.UnprocessableContentException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class IdempotencyRecordTest {

    record Buy(Integer size) {
    }

    private final IdempotencyRecord record = new IdempotencyRecord(1, IdempotentOperation.PLACE_ORDER,
            new IdempotencyKey("k-1"), RequestHash.of(new Buy(10)), 42, Instant.parse("2026-11-10T14:30:00Z"));

    @Test
    void theSameRequestGetsTheResourceItCreated() {
        assertThat(record.resourceFor(RequestHash.of(new Buy(10)))).isEqualTo(42);
    }

    @Test
    void aDifferentRequestCannotReuseTheKey() {
        assertThatThrownBy(() -> record.resourceFor(RequestHash.of(new Buy(11))))
                .isInstanceOf(UnprocessableContentException.class)
                .hasMessageContaining("k-1")
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }
}
