package dev.cuccorese.brokerage.shared.idempotency;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * SHA-256 of a parsed command, so two requests that ask for the same thing match whatever their
 * JSON looked like: field order, spacing, or {@code 1000} against {@code 1000.00}. Every record
 * component counts, including ones added later, so a new field can never be left out of the
 * comparison by mistake.
 */
public record Fingerprint(String sha256) {

    public static Fingerprint of(Record command) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical(command).getBytes(StandardCharsets.UTF_8));
            return new Fingerprint(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM provides SHA-256", e);
        }
    }

    private static String canonical(Object value) {
        return switch (value) {
            case null -> "null";
            case Record record -> record.getClass().getSimpleName() + Arrays.stream(record.getClass().getRecordComponents())
                    .map(component -> component.getName() + "=" + canonical(valueOf(record, component)))
                    .collect(Collectors.joining(",", "(", ")"));
            case BigDecimal number -> number.stripTrailingZeros().toPlainString();
            case String text -> '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
            default -> value.toString();
        };
    }

    private static Object valueOf(Record record, RecordComponent component) {
        try {
            Method accessor = component.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + component.getName(), e);
        }
    }
}
