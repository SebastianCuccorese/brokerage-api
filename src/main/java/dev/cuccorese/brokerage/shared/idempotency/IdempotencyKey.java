package dev.cuccorese.brokerage.shared.idempotency;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A client's idempotency key, from the {@code Idempotency-Key} header. The IETF draft defines the
 * header as a structured-field string, so the quoted {@code "abc"} and the bare {@code abc} are the
 * same key. The allowed characters are narrow so a key can be echoed in an error or a log safely.
 */
public record IdempotencyKey(String value) {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    public static final String SYNTAX = "[A-Za-z0-9._:-]{1,64}|\"[A-Za-z0-9._:-]{1,64}\"";
    public static final String SYNTAX_MESSAGE = "must be 1 to 64 characters of A-Z, a-z, 0-9, '.', '_', ':' or '-'";

    private static final Pattern VALUE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    public IdempotencyKey {
        if (!VALUE.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a valid idempotency key");
        }
    }

    /** Empty when the header is absent; a present header has already been checked against {@link #SYNTAX}. */
    public static Optional<IdempotencyKey> fromHeader(String header) {
        return Optional.ofNullable(header)
                .map(value -> value.startsWith("\"") ? value.substring(1, value.length() - 1) : value)
                .map(IdempotencyKey::new);
    }
}
