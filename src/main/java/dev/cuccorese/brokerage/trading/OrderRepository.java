package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Currencies;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    List<Order> findByUserIdAndStatusInOrderByDatetimeAscIdAsc(Integer userId, Collection<OrderStatus> statuses);

    /** The user's cash and positions as their filled and open orders leave them; callers decide on locking. */
    default Ledger ledgerOf(Integer userId) {
        return Ledger.replay(Currencies.ARS, findByUserIdAndStatusInOrderByDatetimeAscIdAsc(
                userId, EnumSet.of(OrderStatus.FILLED, OrderStatus.NEW)));
    }
}
