package dev.cuccorese.brokerage.shared;

/**
 * Stable, machine-readable reason of every error the API answers. Clients switch on these names,
 * so a code is never renamed or reused for another meaning; the human text can change freely.
 */
public enum ErrorCode {
    USER_NOT_FOUND,
    INSTRUMENT_NOT_FOUND,
    ORDER_NOT_FOUND,
    INSTRUMENT_NOT_TRADABLE,
    SIDE_NOT_TRADABLE,
    SIZE_OR_AMOUNT_REQUIRED,
    LIMIT_PRICE_MISMATCH,
    ORDER_TOO_LARGE,
    ORDER_NOT_CANCELLABLE,
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    INTERNAL_ERROR
}
