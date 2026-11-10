package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Currencies;
import java.util.EnumSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds a user's {@link Ledger} from everything that moves their cash and shares; callers decide on locking. */
@Component
@RequiredArgsConstructor
class Ledgers {

    private final TransferRepository transfers;
    private final OrderRepository orders;

    Ledger of(Integer userId) {
        return Ledger.replay(Currencies.ARS,
                transfers.findByUserIdAndStatus(userId, TransferStatus.COMPLETED),
                orders.findByUserIdAndStatusInOrderByDatetimeAscIdAsc(
                        userId, EnumSet.of(OrderStatus.FILLED, OrderStatus.NEW)));
    }
}
