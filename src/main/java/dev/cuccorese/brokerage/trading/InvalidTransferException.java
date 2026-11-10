package dev.cuccorese.brokerage.trading;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** A transfer that cannot be made as sent; nothing is stored. */
public class InvalidTransferException extends ErrorResponseException {

    public InvalidTransferException(String detail) {
        super(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail), null);
    }
}
