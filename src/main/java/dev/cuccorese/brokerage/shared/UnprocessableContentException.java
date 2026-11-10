package dev.cuccorese.brokerage.shared;

/**
 * A well-formed request that contradicts an earlier one the server remembers, such as an
 * idempotency key sent again with a different body; nothing is stored. It is not a
 * {@link ConflictException}: the client cannot fix it by retrying later, only by changing the request.
 */
public non-sealed class UnprocessableContentException extends DomainException {

    public UnprocessableContentException(ErrorCode code, String message) {
        super(code, message);
    }
}
