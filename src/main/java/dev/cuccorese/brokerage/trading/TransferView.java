package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Money;
import java.time.Instant;

/** {@code rejectionReason} says why a withdrawal was rejected; null for a completed transfer. */
public record TransferView(
        Integer id,
        Integer userId,
        TransferType type,
        Money amount,
        TransferStatus status,
        TransferRejectionReason rejectionReason,
        Instant datetime) {

    static TransferView of(Transfer transfer) {
        return new TransferView(transfer.getId(), transfer.getUserId(), transfer.getType(),
                transfer.getAmount().rounded(), transfer.getStatus(), transfer.getRejectionReason(),
                transfer.getDatetime());
    }
}
