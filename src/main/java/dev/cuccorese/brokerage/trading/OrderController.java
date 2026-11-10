package dev.cuccorese.brokerage.trading;

import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
class OrderController {

    private final OrderService orders;

    /** Answers 201 for every order it stores, including the ones placed as REJECTED. */
    @PostMapping
    ResponseEntity<OrderView> place(@Valid @RequestBody PlaceOrderRequest request) {
        OrderView order = OrderView.of(orders.place(request.toPlaceOrder()));
        return ResponseEntity.created(URI.create("/orders/" + order.id())).body(order);
    }

    @GetMapping("/{orderId}")
    OrderView get(@PathVariable Integer orderId) {
        return OrderView.of(orders.get(orderId));
    }
}
