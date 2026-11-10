package dev.cuccorese.brokerage.trading;

public enum RejectionReason {
    NO_MARKET_DATA,
    AMOUNT_BELOW_ONE_SHARE,
    INSUFFICIENT_CASH,
    INSUFFICIENT_SHARES
}
