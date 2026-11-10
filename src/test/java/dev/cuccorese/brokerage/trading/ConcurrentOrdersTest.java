package dev.cuccorese.brokerage.trading;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cuccorese.brokerage.TestcontainersConfiguration;
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

    /** PAMP last closed at 925.85, so 1000 pesos pay for one share and not for two. */
    @Test
    void twoOrdersAtOnceCannotSpendTheSameCash() throws Exception {
        int user = new TestAccounts(jdbc).fundedUser(1_000);
        PlaceOrder oneShare = new PlaceOrder(user, PAMP, OrderSide.BUY, OrderType.MARKET, 1, null, null);
        CountDownLatch start = new CountDownLatch(1);
        Callable<OrderStatus> placeOnStart = () -> {
            start.await();
            return orders.place(oneShare).getStatus();
        };

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<OrderStatus>> results = List.of(pool.submit(placeOnStart), pool.submit(placeOnStart));
            start.countDown();

            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                    .containsExactlyInAnyOrder(OrderStatus.FILLED, OrderStatus.REJECTED);
        }
    }
}
