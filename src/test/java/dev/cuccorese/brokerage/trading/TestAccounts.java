package dev.cuccorese.brokerage.trading;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Creates a fresh user per test, so tests never touch the seed users whose exact
 * portfolio other tests assert. Rows are inserted with dates before any new order.
 */
class TestAccounts {

    private static final int PESOS = 66;

    private final JdbcClient jdbc;

    TestAccounts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    int newUser() {
        return jdbc.sql("insert into users (email, accountnumber) values ('test@example.com', 'test') returning id")
                .query(Integer.class)
                .single();
    }

    /** A new user with {@code pesos} deposited. */
    int fundedUser(int pesos) {
        int userId = newUser();
        insertFilled(userId, PESOS, "CASH_IN", pesos, 1);
        return userId;
    }

    /** A new user who holds {@code shares} of the instrument and no cash. */
    int holder(int instrumentId, int shares) {
        int userId = newUser();
        insertFilled(userId, PESOS, "CASH_IN", shares, 1);
        insertFilled(userId, instrumentId, "BUY", shares, 1);
        return userId;
    }

    int orderCount(int userId) {
        return jdbc.sql("select count(*) from orders where userid = ?").param(userId).query(Integer.class).single();
    }

    String storedDatetime(int orderId) {
        return jdbc.sql("select datetime::text from orders where id = ?").param(orderId).query(String.class).single();
    }

    private void insertFilled(int userId, int instrumentId, String side, int size, int price) {
        jdbc.sql("""
                insert into orders (instrumentid, userid, size, price, type, side, status, datetime)
                values (?, ?, ?, ?, 'MARKET', ?, 'FILLED', '2023-07-12 10:00:00')
                """)
                .params(instrumentId, userId, size, price, side)
                .update();
    }
}
