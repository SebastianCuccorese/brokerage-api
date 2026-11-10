package dev.cuccorese.brokerage.shared;

import lombok.Getter;

/**
 * A business rule that refused a request. It carries a stable {@link ErrorCode} and its human
 * message but knows nothing about HTTP: the category subclasses say what kind of refusal it is
 * and {@code ApiExceptionHandler} is the only place that turns a category into a status. The
 * hierarchy is sealed so a new category cannot be added without deciding its status there.
 */
@Getter
public abstract sealed class DomainException extends RuntimeException
        permits NotFoundException, InvalidRequestException, ConflictException, UnprocessableContentException {

    private final ErrorCode code;

    protected DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
