package dev.cuccorese.brokerage.shared;

/** A valid request that the current state of a resource does not allow. */
public non-sealed class ConflictException extends DomainException {

    public ConflictException(ErrorCode code, String message) {
        super(code, message);
    }
}
