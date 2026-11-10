package dev.cuccorese.brokerage.shared;

/** A request that cannot be honoured as sent, whatever the state of the system; nothing is stored. */
public non-sealed class InvalidRequestException extends DomainException {

    public InvalidRequestException(ErrorCode code, String message) {
        super(code, message);
    }
}
