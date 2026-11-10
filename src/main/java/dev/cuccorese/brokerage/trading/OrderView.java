package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Money;
import java.time.LocalDateTime;

/** {@code rejectionReason} says why the market rejected the order; null for any other status. */
public record OrderView(
        Integer id,
        Integer userId,
        Integer instrumentId,
        OrderSide side,
        OrderType type,
        Integer size,
        Money price,
        OrderStatus status,
        LocalDateTime datetime,
        RejectionReason rejectionReason) {

    static OrderView of(Order order) {
        Money price = order.getPrice() == null ? null : order.getPrice().rounded();
        return new OrderView(order.getId(), order.getUserId(), order.getInstrumentId(), order.getSide(),
                order.getType(), order.getSize(), price, order.getStatus(), order.getDatetime(),
                order.getRejectionReason());
    }
}
