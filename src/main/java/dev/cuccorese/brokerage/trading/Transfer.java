package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.ErrorCode;
import dev.cuccorese.brokerage.shared.InvalidRequestException;
import dev.cuccorese.brokerage.shared.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Cash a user puts in or takes out. It is not an order: no instrument, price or market is
 * involved. A withdrawal the user cannot cover is still a transfer: it is kept REJECTED with its
 * reason, as a rejected order is.
 */
@Entity
@Table(name = "transfers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "userid")
    private Integer userId;

    @Enumerated(EnumType.STRING)
    private TransferType type;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TransferStatus status;

    @Column(name = "rejectionreason")
    @Enumerated(EnumType.STRING)
    private TransferRejectionReason rejectionReason;

    private Instant datetime;

    private Transfer(Integer userId, TransferType type, Money amount, Instant at) {
        if (!amount.isGreaterThan(Money.zero(amount.currency()))) {
            throw new InvalidRequestException(ErrorCode.TRANSFER_AMOUNT_NOT_POSITIVE, "amount must be positive");
        }
        this.userId = userId;
        this.type = type;
        this.amount = amount.amount();
        this.status = TransferStatus.COMPLETED;
        this.datetime = at;
    }

    /** @throws InvalidRequestException when {@code amount} is not positive */
    public static Transfer cashIn(Integer userId, Money amount, Instant at) {
        return new Transfer(userId, TransferType.CASH_IN, amount, at);
    }

    /**
     * Takes {@code amount} out of what the user's {@code ledger} has available, so the cash that
     * open limit buys reserve stays with them. Beyond that it comes back REJECTED.
     *
     * @throws InvalidRequestException when {@code amount} is not positive
     */
    public static Transfer cashOut(Integer userId, Money amount, Ledger ledger, Instant at) {
        Transfer transfer = new Transfer(userId, TransferType.CASH_OUT, amount, at);
        if (amount.isGreaterThan(ledger.availableCash())) {
            transfer.status = TransferStatus.REJECTED;
            transfer.rejectionReason = TransferRejectionReason.INSUFFICIENT_CASH;
        }
        return transfer;
    }

    public Money getAmount() {
        return Money.of(Currencies.ARS, amount);
    }
}
