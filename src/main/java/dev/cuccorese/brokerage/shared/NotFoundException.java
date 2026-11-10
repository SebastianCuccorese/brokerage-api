package dev.cuccorese.brokerage.shared;

/** A resource the request names does not exist. */
public non-sealed class NotFoundException extends DomainException {

    public NotFoundException(ErrorCode code, String resource, Object id) {
        super(code, resource + " " + id + " not found");
    }
}
