package dev.cuccorese.brokerage.trading;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** An order that cannot be placed as sent; nothing is stored. */
public class InvalidOrderException extends ErrorResponseException {

    public InvalidOrderException(String detail) {
        super(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail), null);
    }
}
