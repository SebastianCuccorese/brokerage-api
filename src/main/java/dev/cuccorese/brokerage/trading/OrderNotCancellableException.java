package dev.cuccorese.brokerage.trading;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class OrderNotCancellableException extends ErrorResponseException {

    public OrderNotCancellableException(Integer orderId, OrderStatus status) {
        super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Order " + orderId + " is " + status + "; only NEW orders can be cancelled"), null);
    }
}
