package dev.cuccorese.brokerage.shared.idempotency;

/** What a key was used for: the same key may stand for an order and, separately, for a transfer. */
public enum IdempotentOperation {
    PLACE_ORDER,
    PLACE_TRANSFER
}
