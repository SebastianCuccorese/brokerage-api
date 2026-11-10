package dev.cuccorese.brokerage.trading;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.cuccorese.brokerage.FixedClockConfiguration;
import dev.cuccorese.brokerage.TestcontainersConfiguration;
import dev.cuccorese.brokerage.shared.idempotency.IdempotencyKey;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Retries of {@code POST /orders} with an {@code Idempotency-Key}; PAMP (47) last closed at 925.85. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class IdempotentOrdersTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    private TestAccounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new TestAccounts(jdbc);
    }

    private MvcTestResult place(String key, String json) {
        return mvc.post().uri("/orders").contentType(MediaType.APPLICATION_JSON)
                .header(IdempotencyKey.HEADER, key).content(json).exchange();
    }

    private MvcTestResult placeWithoutKey(String json) {
        return mvc.post().uri("/orders").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String buy(int user, int size) {
        return """
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "size": %d}
                """.formatted(user, size);
    }

    private static int idOf(MvcTestResult result) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.id");
    }

    @Test
    void theSameKeyTwicePlacesOneOrderAndReplaysIt() {
        int user = accounts.fundedUser(10_000);

        MvcTestResult first = place("buy-1", buy(user, 1));
        MvcTestResult retry = place("buy-1", buy(user, 1));

        assertThat(first).hasStatus(HttpStatus.CREATED).doesNotContainHeader(IdempotencyKey.REPLAYED_HEADER);
        assertThat(retry).hasStatus(HttpStatus.CREATED)
                .hasHeader(IdempotencyKey.REPLAYED_HEADER, "true")
                .hasHeader("Location", "/orders/" + idOf(first))
                .bodyJson().isLenientlyEqualTo("""
                        {"id": %d, "status": "FILLED", "size": 1}
                        """.formatted(idOf(first)));
        assertThat(accounts.tradeCount(user)).isEqualTo(1);
    }

    @Test
    void theSameKeyWithADifferentOrderIsRefusedAndStoresNothing() {
        int user = accounts.fundedUser(10_000);
        place("buy-2", buy(user, 1));

        assertThat(place("buy-2", buy(user, 2))).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().isLenientlyEqualTo("""
                        {"status": 422, "code": "IDEMPOTENCY_KEY_REUSED",
                         "detail": "Idempotency-Key buy-2 was already used for a different request"}
                        """);
        assertThat(accounts.tradeCount(user)).isEqualTo(1);
    }

    @Test
    void fieldOrderSpacingAndScaleDoNotMakeADifferentOrder() {
        int user = accounts.fundedUser(10_000);
        int orderId = idOf(place("buy-3", """
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "amount": 5000}
                """.formatted(user)));

        assertThat(place("buy-3", """
                {  "amount" : 5000.00, "type":"MARKET",
                   "side": "BUY", "instrumentId": 47, "userId": %d }
                """.formatted(user)))
                .hasHeader(IdempotencyKey.REPLAYED_HEADER, "true")
                .bodyJson().extractingPath("$.id").isEqualTo(orderId);
    }

    @Test
    void aQuotedKeyIsTheSameKey() {
        int user = accounts.fundedUser(10_000);
        place("\"buy-4\"", buy(user, 1));

        assertThat(place("buy-4", buy(user, 1))).hasHeader(IdempotencyKey.REPLAYED_HEADER, "true");
        assertThat(accounts.tradeCount(user)).isEqualTo(1);
    }

    @Test
    void anotherUsersKeyDoesNotCollide() {
        int alice = accounts.fundedUser(10_000);
        int bob = accounts.fundedUser(10_000);

        MvcTestResult aliceOrder = place("shared-key", buy(alice, 1));
        MvcTestResult bobOrder = place("shared-key", buy(bob, 1));

        assertThat(bobOrder).hasStatus(HttpStatus.CREATED).doesNotContainHeader(IdempotencyKey.REPLAYED_HEADER);
        assertThat(idOf(bobOrder)).isNotEqualTo(idOf(aliceOrder));
        assertThat(accounts.tradeCount(bob)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "with space", "a/b", "\"unbalanced"})
    void anInvalidKeyIsABadRequestAndStoresNothing(String key) {
        int user = accounts.fundedUser(10_000);

        assertThat(place(key, buy(user, 1))).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().isLenientlyEqualTo("""
                        {"code": "VALIDATION_FAILED", "errors": [{"field": "Idempotency-Key"}]}
                        """);
        assertThat(accounts.tradeCount(user)).isZero();
    }

    @Test
    void aKeyLongerThan64CharactersIsABadRequest() {
        int user = accounts.fundedUser(10_000);

        assertThat(place("x".repeat(65), buy(user, 1))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(accounts.tradeCount(user)).isZero();
    }

    @Test
    void withoutAKeyEveryRequestPlacesAnOrder() {
        int user = accounts.fundedUser(10_000);

        MvcTestResult first = placeWithoutKey(buy(user, 1));
        MvcTestResult second = placeWithoutKey(buy(user, 1));

        assertThat(second).hasStatus(HttpStatus.CREATED).doesNotContainHeader(IdempotencyKey.REPLAYED_HEADER);
        assertThat(idOf(second)).isNotEqualTo(idOf(first));
        assertThat(accounts.tradeCount(user)).isEqualTo(2);
    }

    /** The deposit in between would let the order fill now: the replay must not decide it again. */
    @Test
    void aRejectedOrderReplaysAsRejected() {
        int user = accounts.fundedUser(1_000);
        int orderId = idOf(place("buy-5", buy(user, 2)));
        accounts.deposit(user, 10_000);

        assertThat(place("buy-5", buy(user, 2))).hasStatus(HttpStatus.CREATED)
                .hasHeader(IdempotencyKey.REPLAYED_HEADER, "true")
                .bodyJson().isLenientlyEqualTo("""
                        {"id": %d, "status": "REJECTED", "rejectionReason": "INSUFFICIENT_CASH"}
                        """.formatted(orderId));
        assertThat(accounts.tradeCount(user)).isEqualTo(1);
    }

    @Test
    void aReplayShowsTheOrderAsItIsNow() {
        int user = accounts.fundedUser(10_000);
        String limitBuy = """
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "LIMIT", "size": 1, "price": 900}
                """.formatted(user);
        int orderId = idOf(place("buy-6", limitBuy));
        mvc.post().uri("/orders/{orderId}/cancel", orderId).exchange();

        assertThat(place("buy-6", limitBuy)).hasHeader(IdempotencyKey.REPLAYED_HEADER, "true")
                .bodyJson().isLenientlyEqualTo("""
                        {"id": %d, "status": "CANCELLED"}
                        """.formatted(orderId));
    }

    @Test
    void aRefusedRequestDoesNotUseUpItsKey() {
        int user = accounts.fundedUser(10_000);

        assertThat(place("buy-7", """
                {"userId": %d, "instrumentId": 999999, "side": "BUY", "type": "MARKET", "size": 1}
                """.formatted(user))).hasStatus(HttpStatus.NOT_FOUND);

        assertThat(place("buy-7", buy(user, 1))).hasStatus(HttpStatus.CREATED)
                .doesNotContainHeader(IdempotencyKey.REPLAYED_HEADER);
    }
}
