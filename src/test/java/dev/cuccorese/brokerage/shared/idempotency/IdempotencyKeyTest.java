package dev.cuccorese.brokerage.shared.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdempotencyKeyTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", "8e03978e-40d5-43e8-bc93-6894a57f9324", "order:2026.11.10_1",
            "\"8e03978e-40d5-43e8-bc93-6894a57f9324\""})
    void acceptsShortSafeKeysBareOrQuoted(String header) {
        assertThat(header).matches(IdempotencyKey.SYNTAX);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "with space", "bad\nline", "a/b", "ñandú", "\"unbalanced", "unbalanced\"", "\"\""})
    void rejectsAnythingElse(String header) {
        assertThat(header).doesNotMatch(IdempotencyKey.SYNTAX);
    }

    @Test
    void allowsUpTo64Characters() {
        assertThat("x".repeat(64)).matches(IdempotencyKey.SYNTAX);
        assertThat("x".repeat(65)).doesNotMatch(IdempotencyKey.SYNTAX);
    }

    @Test
    void aQuotedKeyIsTheSameAsTheBareOne() {
        assertThat(IdempotencyKey.fromHeader("\"k-1\"")).contains(new IdempotencyKey("k-1"));
        assertThat(IdempotencyKey.fromHeader("k-1")).contains(new IdempotencyKey("k-1"));
    }

    @Test
    void noHeaderIsNoKey() {
        assertThat(IdempotencyKey.fromHeader(null)).isEmpty();
    }

    @Test
    void refusesToHoldAnUnsafeValue() {
        assertThatThrownBy(() -> new IdempotencyKey("bad\nline")).isInstanceOf(IllegalArgumentException.class);
    }
}
