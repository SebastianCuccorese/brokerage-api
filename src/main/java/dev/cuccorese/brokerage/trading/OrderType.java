package dev.cuccorese.brokerage.trading;

/** Order kinds; {@code acceptedStatus} is where an order that passes the checks starts. */
public enum OrderType {
    MARKET(OrderStatus.FILLED),
    LIMIT(OrderStatus.NEW);

    private final OrderStatus acceptedStatus;

    OrderType(OrderStatus acceptedStatus) {
        this.acceptedStatus = acceptedStatus;
    }

    OrderStatus acceptedStatus() {
        return acceptedStatus;
    }
}
