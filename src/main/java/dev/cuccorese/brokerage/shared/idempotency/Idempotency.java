package dev.cuccorese.brokerage.shared.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remembers what each idempotency key created, so a client that retries after a timeout gets the
 * first result instead of acting twice. Keys are honoured for at least {@link #RETENTION}.
 */
@Component
@RequiredArgsConstructor
public class Idempotency {

    public static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyRecordRepository records;
    private final Clock clock;

    /**
     * Creates the resource once per key: the first time it runs {@code create} and remembers the
     * result; a retry with the same {@code command} gets that resource back from {@code find} without
     * running anything. Without a key it just creates.
     *
     * The caller must hold the user's row lock. That lock already serialises every write of the
     * user, so a concurrent retry waits for the first request and then finds its key; the unique
     * constraint on (user, operation, key) is only the safety net.
     *
     * @throws dev.cuccorese.brokerage.shared.UnprocessableContentException when the key comes back
     *         with a different command
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T extends Identified> IdempotentResult<T> once(Optional<IdempotencyKey> key, IdempotentOperation operation,
            Integer userId, Record command, Supplier<T> create, Function<Integer, Optional<T>> find) {
        if (key.isEmpty()) {
            return IdempotentResult.created(create.get());
        }
        RequestHash request = RequestHash.of(command);
        Optional<IdempotencyRecord> earlier = records.findByUserIdAndOperationAndIdempotencyKey(
                userId, operation, key.get().value());
        if (earlier.isPresent()) {
            return IdempotentResult.replayed(find.apply(earlier.get().resourceFor(request)).orElseThrow());
        }
        T created = create.get();
        records.save(new IdempotencyRecord(userId, operation, key.get(), request, created.getId(), Instant.now(clock)));
        return IdempotentResult.created(created);
    }

    /** Hourly, so a key may outlive {@link #RETENTION} by up to an hour; every instance may run it. */
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void purgeExpired() {
        records.deleteCreatedBefore(Instant.now(clock).minus(RETENTION));
    }
}
