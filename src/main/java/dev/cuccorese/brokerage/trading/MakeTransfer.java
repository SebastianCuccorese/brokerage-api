package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Money;

/** What a user asks to move: {@code amount} pesos in (CASH_IN) or out (CASH_OUT). */
public record MakeTransfer(Integer userId, TransferType type, Money amount) {
}
