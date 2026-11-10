package dev.cuccorese.brokerage.trading;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    List<Order> findByUserIdAndStatusInOrderByDatetimeAscIdAsc(Integer userId, Collection<OrderStatus> statuses);
}
