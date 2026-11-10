package dev.cuccorese.brokerage.trading;

import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.Money;
import java.math.BigDecimal;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Creates a fresh user per test, so tests never touch the seed users whose exact
 * portfolio other tests assert. Cash comes in as real deposits; shares are inserted as
 * filled buys dated before any new order.
 */
class TestAccounts {

    private final JdbcClient jdbc;
    private final TransferService transfers;

    TestAccounts(JdbcClient jdbc, TransferService transfers) {
        this.jdbc = jdbc;
        this.transfers = transfers;
    }

    int newUser() {
        return jdbc.sql("insert into users (email, accountnumber) values ('test@example.com', 'test') returning id")
                .query(Integer.class)
                .single();
    }

    /** A new user with {@code pesos} deposited. */
    int fundedUser(int pesos) {
        int userId = newUser();
        deposit(userId, pesos);
        return userId;
    }

    /** A new user who holds {@code shares} of the instrument and no cash. */
    int holder(int instrumentId, int shares) {
        int userId = newUser();
        deposit(userId, shares);
        jdbc.sql("""
                insert into orders (instrumentid, userid, size, price, type, side, status, datetime)
                values (?, ?, ?, 1, 'MARKET', 'BUY', 'FILLED', '2023-07-12 10:00:00-03')
                """)
                .params(instrumentId, userId, shares)
                .update();
        return userId;
    }

    int orderCount(int userId) {
        return jdbc.sql("select count(*) from orders where userid = ?").param(userId).query(Integer.class).single();
    }

    int transferCount(int userId) {
        return jdbc.sql("select count(*) from transfers where userid = ?").param(userId).query(Integer.class).single();
    }

    /** The stored time as UTC wall-clock text, whatever the session's time zone. */
    String storedDatetimeInUtc(int orderId) {
        return jdbc.sql("select (datetime at time zone 'UTC')::text from orders where id = ?")
                .param(orderId)
                .query(String.class)
                .single();
    }

    void deposit(int userId, int pesos) {
        transfers.make(userId, TransferType.DEPOSIT, Money.of(Currencies.ARS, BigDecimal.valueOf(pesos)));
    }
}
