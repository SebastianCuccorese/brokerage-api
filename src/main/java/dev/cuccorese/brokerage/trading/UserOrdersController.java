package dev.cuccorese.brokerage.trading;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class UserOrdersController {

    private final OrderService orders;

    @GetMapping("/users/{userId}/orders")
    Page<OrderView> list(@PathVariable Integer userId, @RequestParam Optional<OrderStatus> status, Pageable pageable) {
        return orders.listOf(userId, status, pageable).map(OrderView::of);
    }
}
