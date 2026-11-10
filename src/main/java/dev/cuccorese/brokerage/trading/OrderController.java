package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.idempotency.IdempotencyKey;
import dev.cuccorese.brokerage.shared.idempotency.IdempotentResult;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
class OrderController {

    private final OrderService orders;

    /**
     * Answers 201 for every order it stores, including the ones placed as REJECTED, and again for
     * a retry with the same idempotency key, marked {@code Idempotent-Replayed: true}.
     */
    @PostMapping
    ResponseEntity<OrderView> place(@Valid @RequestBody PlaceOrderRequest request,
            @Parameter(description = "Optional. A retry with the same key returns the first order instead of placing"
                    + " another; the key is kept at least 24 hours and cannot be reused for a different order.")
            @RequestHeader(name = IdempotencyKey.HEADER, required = false)
            @Pattern(regexp = IdempotencyKey.SYNTAX, message = IdempotencyKey.SYNTAX_MESSAGE) String idempotencyKey) {
        IdempotentResult<Order> placed = orders.place(request.toPlaceOrder(), IdempotencyKey.fromHeader(idempotencyKey));
        OrderView order = OrderView.of(placed.resource());
        ResponseEntity.BodyBuilder response = ResponseEntity.created(URI.create("/orders/" + order.id()));
        if (placed.replayed()) {
            response.header(IdempotencyKey.REPLAYED_HEADER, "true");
        }
        return response.body(order);
    }

    @GetMapping("/{orderId}")
    OrderView get(@PathVariable Integer orderId) {
        return OrderView.of(orders.get(orderId));
    }

    @PostMapping("/{orderId}/cancel")
    OrderView cancel(@PathVariable Integer orderId) {
        return OrderView.of(orders.cancel(orderId));
    }
}
