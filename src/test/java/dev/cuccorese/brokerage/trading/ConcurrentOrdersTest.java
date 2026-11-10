package dev.cuccorese.brokerage.trading;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cuccorese.brokerage.TestcontainersConfiguration;
import dev.cuccorese.brokerage.shared.ConflictException;
import dev.cuccorese.brokerage.shared.Currencies;
import dev.cuccorese.brokerage.shared.Money;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConcurrentOrdersTest {

    private static final int PAMP = 47;

    @Autowired
    private OrderService orders;

    @Autowired
    private TransferService transfers;

    @Autowired
    private JdbcClient jdbc;

    private TestAccounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new TestAccounts(jdbc, transfers);
    }

    /** Runs each task on its own thread, all released at the same instant, and returns their results in order. */
    private static <T> List<T> atOnce(List<Callable<T>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    private static <T> List<T> twiceAtOnce(Callable<T> task) throws Exception {
        return atOnce(List.of(task, task));
    }

    /** PAMP last closed at 925.85, so 1000 pesos pay for one share and not for two. */
    @Test
    void twoOrdersAtOnceCannotSpendTheSameCash() throws Exception {
        int user = accounts.fundedUser(1_000);
        PlaceOrder oneShare = new PlaceOrder(user, PAMP, OrderSide.BUY, OrderType.MARKET, 1, null, null);

        assertThat(twiceAtOnce(() -> orders.place(oneShare).getStatus()))
                .containsExactlyInAnyOrder(OrderStatus.FILLED, OrderStatus.REJECTED);
    }

    @Test
    void twoCancelsAtOnceCancelTheOrderOnce() throws Exception {
        int user = accounts.fundedUser(1_000);
        Money price = Money.of(Currencies.ARS, new BigDecimal("900"));
        Integer orderId = orders.place(new PlaceOrder(user, PAMP, OrderSide.BUY, OrderType.LIMIT, 1, null, price))
                .getId();

        assertThat(twiceAtOnce(() -> {
            try {
                return orders.cancel(orderId).getStatus().name();
            } catch (ConflictException e) {
                return "NOT_CANCELLABLE";
            }
        })).containsExactlyInAnyOrder("CANCELLED", "NOT_CANCELLABLE");
    }

    /** Either one alone fits in the 1000 pesos: the withdrawal takes all of them, the buy one share at 925.85. */
    @Test
    void aWithdrawalAndABuyAtOnceCannotSpendTheSameCash() throws Exception {
        int user = accounts.fundedUser(1_000);
        Money everything = Money.of(Currencies.ARS, new BigDecimal("1000"));
        PlaceOrder buyForEverything = new PlaceOrder(user, PAMP, OrderSide.BUY, OrderType.MARKET, null, everything, null);

        List<String> outcomes = atOnce(List.of(
                () -> "withdrawal " + transfers.make(user, TransferType.CASH_OUT, everything).getStatus(),
                () -> "buy " + orders.place(buyForEverything).getStatus()));

        assertThat(outcomes).isIn(
                List.of("withdrawal COMPLETED", "buy REJECTED"),
                List.of("withdrawal REJECTED", "buy FILLED"));
    }
}
