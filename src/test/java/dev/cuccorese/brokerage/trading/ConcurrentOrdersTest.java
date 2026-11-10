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
    private JdbcClient jdbc;

    /** Runs {@code task} on two threads released at the same instant and returns both results. */
    private static <T> List<T> twiceAtOnce(Callable<T> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<T> onStart = () -> {
            start.await();
            return task.call();
        };
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<T>> futures = List.of(pool.submit(onStart), pool.submit(onStart));
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    /** PAMP last closed at 925.85, so 1000 pesos pay for one share and not for two. */
    @Test
    void twoOrdersAtOnceCannotSpendTheSameCash() throws Exception {
        int user = new TestAccounts(jdbc).fundedUser(1_000);
        PlaceOrder oneShare = new PlaceOrder(user, PAMP, OrderSide.BUY, OrderType.MARKET, 1, null, null);

        assertThat(twiceAtOnce(() -> orders.place(oneShare).getStatus()))
                .containsExactlyInAnyOrder(OrderStatus.FILLED, OrderStatus.REJECTED);
    }

    @Test
    void twoCancelsAtOnceCancelTheOrderOnce() throws Exception {
        int user = new TestAccounts(jdbc).fundedUser(1_000);
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
}
