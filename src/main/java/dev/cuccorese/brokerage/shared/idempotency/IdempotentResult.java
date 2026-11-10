package dev.cuccorese.brokerage.shared.idempotency;

/**
 * The resource a request created or, when {@code replayed}, the one an earlier request with the same
 * key created, as it is now (an order cancelled since then comes back CANCELLED).
 */
public record IdempotentResult<T>(T resource, boolean replayed) {

    static <T> IdempotentResult<T> created(T resource) {
        return new IdempotentResult<>(resource, false);
    }

    static <T> IdempotentResult<T> replayed(T resource) {
        return new IdempotentResult<>(resource, true);
    }
}
