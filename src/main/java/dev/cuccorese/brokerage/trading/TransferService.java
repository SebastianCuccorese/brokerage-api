package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Money;
import dev.cuccorese.brokerage.shared.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final UserRepository users;
    private final TransferRepository transfers;
    private final Ledgers ledgers;
    private final Clock clock;

    /**
     * Locks the user's row as placing an order does, so a withdrawal and an order of the same user
     * are decided one after the other and together cannot spend more than the cash.
     */
    @Transactional
    public Transfer make(Integer userId, TransferType type, Money amount) {
        users.findWithLockById(userId).orElseThrow(() -> new NotFoundException("User", userId));
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Transfer transfer = switch (type) {
            case DEPOSIT -> Transfer.deposit(userId, amount, now);
            case WITHDRAWAL -> Transfer.withdraw(userId, amount, ledgers.of(userId), now);
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
                .orElseThrow(() -> new NotFoundException("Transfer", transferId));
    }

    private void requireUser(Integer userId) {
        if (!users.existsById(userId)) {
            throw new NotFoundException("User", userId);
        }
    }
}
