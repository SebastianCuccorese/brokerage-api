package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.ErrorCode;
import dev.cuccorese.brokerage.shared.NotFoundException;
import dev.cuccorese.brokerage.shared.idempotency.Idempotency;
import dev.cuccorese.brokerage.shared.idempotency.IdempotencyKey;
import dev.cuccorese.brokerage.shared.idempotency.IdempotentOperation;
import dev.cuccorese.brokerage.shared.idempotency.IdempotentResult;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final UserRepository users;
    private final TransferRepository transfers;
    private final Ledgers ledgers;
    private final Idempotency idempotency;
    private final Clock clock;

    /**
     * Locks the user's row as placing an order does, so a withdrawal and an order of the same user
     * are decided one after the other and together cannot spend more than the cash. As with
     * orders, a retry with the same idempotency key gets the first transfer back, a REJECTED
     * withdrawal included, without deciding again.
     */
    @Transactional
    public IdempotentResult<Transfer> make(MakeTransfer request, Optional<IdempotencyKey> key) {
        Integer userId = request.userId();
        users.findWithLockById(userId).orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, "User", userId));
        return idempotency.once(key, IdempotentOperation.PLACE_TRANSFER, userId, request,
                () -> decide(request), transfers::findById);
    }

    private Transfer decide(MakeTransfer request) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Transfer transfer = switch (request.type()) {
            case CASH_IN -> Transfer.cashIn(request.userId(), request.amount(), now);
            case CASH_OUT -> Transfer.cashOut(request.userId(), request.amount(), ledgers.of(request.userId()), now);
        };
        return transfers.save(transfer);
    }

    /** Newest first. */
    @Transactional(readOnly = true)
    public List<Transfer> historyOf(Integer userId) {
        requireUser(userId);
        return transfers.findByUserIdOrderByDatetimeDescIdDesc(userId);
    }

    /** @throws NotFoundException also when the transfer exists but is another user's */
    @Transactional(readOnly = true)
    public Transfer get(Integer userId, Integer transferId) {
        requireUser(userId);
        return transfers.findByIdAndUserId(transferId, userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TRANSFER_NOT_FOUND, "Transfer", transferId));
    }

    private void requireUser(Integer userId) {
        if (!users.existsById(userId)) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND, "User", userId);
        }
    }
}
