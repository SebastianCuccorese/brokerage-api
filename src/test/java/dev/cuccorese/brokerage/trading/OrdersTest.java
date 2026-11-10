package dev.cuccorese.brokerage.trading;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.cuccorese.brokerage.FixedClockConfiguration;
import dev.cuccorese.brokerage.TestcontainersConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Places orders through the API against the provided seed. Every test works on users of its
 * own; PAMP (47) last closed at 925.85 and PGR (3) has no market data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class OrdersTest {

    private static final int PAMP = 47;
    private static final int PGR = 3;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    private TestAccounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new TestAccounts(jdbc);
    }

    private MvcTestResult place(String json) {
        return mvc.post().uri("/orders").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult portfolio(int userId) {
        return mvc.get().uri("/users/{userId}/portfolio", userId).exchange();
    }

    private MvcTestResult cancel(int orderId) {
        return mvc.post().uri("/orders/{orderId}/cancel", orderId).exchange();
    }

    private static int idOf(MvcTestResult result) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.id");
    }

    @Test
    void aMarketBuyFillsAtTheLatestCloseAndShowsInThePortfolio() {
        int user = accounts.fundedUser(10_000);

        MvcTestResult result = place("""
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "size": 10}
                """.formatted(user));

        assertThat(result).hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "/orders/" + idOf(result))
                .bodyJson().isLenientlyEqualTo("""
                        {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "size": 10,
                         "price": {"currency": "ARS", "amount": 925.85},
                         "status": "FILLED", "datetime": "2026-11-10T14:30:00Z", "rejectionReason": null}
                        """.formatted(user));
        assertThat(portfolio(user)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"totalValue": {"currency": "ARS", "amount": 10000.00},
                 "availableCash": {"currency": "ARS", "amount": 741.50},
                 "positions": [{"instrumentId": 47, "quantity": 10,
                                "marketValue": {"currency": "ARS", "amount": 9258.50}}]}
                """);
    }

    @Test
    void aLimitBuyStaysOpenAtItsPriceAndReservesTheCash() {
        int user = accounts.fundedUser(10_000);

        assertThat(place("""
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "LIMIT", "size": 10, "price": 900}
                """.formatted(user)))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().isLenientlyEqualTo("""
                        {"size": 10, "price": {"currency": "ARS", "amount": 900.00}, "status": "NEW"}
                        """);
        assertThat(portfolio(user)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"totalValue": {"currency": "ARS", "amount": 10000.00},
                 "availableCash": {"currency": "ARS", "amount": 1000.00},
                 "positions": []}
                """);
    }

    @Test
    void aBuyByAmountTakesTheWholeSharesItAffords() {
        int user = accounts.fundedUser(10_000);

        assertThat(place("""
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "amount": 5000}
                """.formatted(user)))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().isLenientlyEqualTo("""
                        {"size": 5, "price": {"currency": "ARS", "amount": 925.85}, "status": "FILLED"}
                        """);
        assertThat(portfolio(user)).bodyJson().extractingPath("$.availableCash.amount").isEqualTo(5370.75);
    }

    @Test
    void anAmountBelowOneShareIsRejected() {
        int user = accounts.fundedUser(10_000);

        assertThat(place("""
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "amount": 900}
                """.formatted(user)))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().isLenientlyEqualTo("""
                        {"size": 0, "status": "REJECTED", "rejectionReason": "AMOUNT_BELOW_ONE_SHARE"}
                        """);
    }

    @Test
    void aBuyBeyondTheAvailableCashIsStoredAsRejected() {
        int user = accounts.fundedUser(1_000);

        MvcTestResult result = place("""
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "size": 2}
                """.formatted(user));

        assertThat(result).hasStatus(HttpStatus.CREATED)
                .bodyJson().isLenientlyEqualTo("""
                        {"size": 2, "price": {"currency": "ARS", "amount": 925.85},
                         "status": "REJECTED", "rejectionReason": "INSUFFICIENT_CASH"}
                        """);
        assertThat(mvc.get().uri("/orders/{orderId}", idOf(result))).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"status": "REJECTED", "rejectionReason": "INSUFFICIENT_CASH"}
                        """);
        assertThat(portfolio(user)).bodyJson().extractingPath("$.availableCash.amount").isEqualTo(1000.00);
    }

    @Test
    void openLimitBuysCountAgainstTheCash() {
        int user = accounts.fundedUser(1_000);
        place("""
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "LIMIT", "size": 1, "price": 600}
                """.formatted(user));

        assertThat(place("""
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "LIMIT", "size": 1, "price": 500}
                """.formatted(user)))
                .bodyJson().extractingPath("$.rejectionReason").isEqualTo("INSUFFICIENT_CASH");
    }

    @Test
    void aSellBeyondTheAvailableSharesIsRejectedCountingOpenSells() {
        int user = accounts.holder(PAMP, 10);
        assertThat(place("""
                {"userId": %d, "instrumentId": 47, "side": "SELL", "type": "LIMIT", "size": 6, "price": 1000}
                """.formatted(user)))
                .bodyJson().extractingPath("$.status").isEqualTo("NEW");

        assertThat(place("""
                {"userId": %d, "instrumentId": 47, "side": "SELL", "type": "MARKET", "size": 5}
                """.formatted(user)))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().isLenientlyEqualTo("""
                        {"status": "REJECTED", "rejectionReason": "INSUFFICIENT_SHARES"}
                        """);
        assertThat(place("""
                {"userId": %d, "instrumentId": 47, "side": "SELL", "type": "MARKET", "size": 4}
                """.formatted(user)))
                .bodyJson().isLenientlyEqualTo("""
                        {"status": "FILLED", "price": {"currency": "ARS", "amount": 925.85}}
                        """);
    }

    @Test
    void aMarketOrderWithoutMarketDataIsRejected() {
        int user = accounts.fundedUser(10_000);

        assertThat(place("""
                {"userId": %d, "instrumentId": %d, "side": "BUY", "type": "MARKET", "size": 1}
                """.formatted(user, PGR)))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().isLenientlyEqualTo("""
                        {"size": 1, "price": null, "status": "REJECTED", "rejectionReason": "NO_MARKET_DATA"}
                        """);
    }

    @Test
    void storesTheOrderTimeAsAnInstant() {
        int user = accounts.fundedUser(10_000);

        int orderId = idOf(place("""
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "size": 1}
                """.formatted(user)));

        assertThat(accounts.storedDatetimeInUtc(orderId)).isEqualTo("2026-11-10 14:30:00");
    }

    static Stream<Arguments> invalidOrders() {
        return Stream.of(
                Arguments.of("malformed JSON", """
                        {"userId": %d, "instrumentId": 47, "side": "BUY",
                        """),
                Arguments.of("unknown side", """
                        {"userId": %d, "instrumentId": 47, "side": "HOLD", "type": "MARKET", "size": 1}
                        """),
                Arguments.of("missing type", """
                        {"userId": %d, "instrumentId": 47, "side": "BUY", "size": 1}
                        """),
                Arguments.of("both size and amount", """
                        {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "size": 1, "amount": 1000}
                        """),
                Arguments.of("neither size nor amount", """
                        {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET"}
                        """),
                Arguments.of("LIMIT without price", """
                        {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "LIMIT", "size": 1}
                        """),
                Arguments.of("MARKET with price", """
                        {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "size": 1, "price": 900}
                        """),
                Arguments.of("CASH_IN", """
                        {"userId": %d, "instrumentId": 66, "side": "CASH_IN", "type": "MARKET", "size": 1000}
                        """),
                Arguments.of("CASH_OUT", """
                        {"userId": %d, "instrumentId": 66, "side": "CASH_OUT", "type": "MARKET", "size": 1000}
                        """),
                Arguments.of("buying pesos", """
                        {"userId": %d, "instrumentId": 66, "side": "BUY", "type": "LIMIT", "size": 1, "price": 1}
                        """),
                Arguments.of("fractional size", """
                        {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "size": 1.5}
                        """),
                Arguments.of("zero size", """
                        {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "size": 0}
                        """),
                Arguments.of("negative amount", """
                        {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "amount": -100}
                        """),
                Arguments.of("amount with three decimals", """
                        {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "amount": 100.001}
                        """),
                Arguments.of("price with three decimals", """
                        {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "LIMIT", "size": 1, "price": 900.001}
                        """),
                Arguments.of("price wider than its column", """
                        {"userId": %d, "instrumentId": 47, "side": "SELL", "type": "LIMIT", "size": 1, "price": 123456789}
                        """),
                Arguments.of("amount buying more shares than an order holds", """
                        {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "LIMIT", "amount": 999999999999, "price": 0.01}
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidOrders")
    void anInvalidOrderIsABadRequestAndStoresNothing(String description, String json) {
        int user = accounts.newUser();

        assertThat(place(json.formatted(user))).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.status").isEqualTo(400);
        assertThat(accounts.orderCount(user)).isZero();
    }

    @Test
    void anUnknownUserIsNotFound() {
        assertThat(place("""
                {"userId": 999999, "instrumentId": 47, "side": "BUY", "type": "MARKET", "size": 1}
                """))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().isLenientlyEqualTo("""
                        {"code": "USER_NOT_FOUND", "detail": "User 999999 not found"}
                        """);
    }

    @Test
    void anUnknownInstrumentIsNotFound() {
        int user = accounts.fundedUser(10_000);

        assertThat(place("""
                {"userId": %d, "instrumentId": 999999, "side": "BUY", "type": "MARKET", "size": 1}
                """.formatted(user)))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().isLenientlyEqualTo("""
                        {"code": "INSTRUMENT_NOT_FOUND", "detail": "Instrument 999999 not found"}
                        """);
        assertThat(accounts.orderCount(user)).isEqualTo(1);
    }

    @Test
    void anOrderCanBeReadBack() {
        int user = accounts.fundedUser(10_000);
        int orderId = idOf(place("""
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "LIMIT", "size": 3, "price": 910.5}
                """.formatted(user)));

        assertThat(mvc.get().uri("/orders/{orderId}", orderId)).hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"id": %d, "userId": %d, "instrumentId": 47, "side": "BUY", "type": "LIMIT", "size": 3,
                         "price": {"currency": "ARS", "amount": 910.50},
                         "status": "NEW", "datetime": "2026-11-10T14:30:00Z", "rejectionReason": null}
                        """.formatted(orderId, user));
    }

    @Test
    void anUnknownOrderIsNotFound() {
        assertThat(mvc.get().uri("/orders/999999")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(cancel(999999)).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().isLenientlyEqualTo("""
                        {"code": "ORDER_NOT_FOUND", "detail": "Order 999999 not found"}
                        """);
    }

    @Test
    void cancellingAnOpenOrderReleasesItsCash() {
        int user = accounts.fundedUser(10_000);
        int orderId = idOf(place("""
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "LIMIT", "size": 10, "price": 900}
                """.formatted(user)));

        assertThat(cancel(orderId)).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"id": %d, "status": "CANCELLED"}
                        """.formatted(orderId));
        assertThat(portfolio(user)).bodyJson().extractingPath("$.availableCash.amount").isEqualTo(10000.00);
    }

    @Test
    void aFilledOrderCannotBeCancelled() {
        int user = accounts.fundedUser(10_000);
        int orderId = idOf(place("""
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "size": 1}
                """.formatted(user)));

        assertThat(cancel(orderId)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().isLenientlyEqualTo("""
                        {"code": "ORDER_NOT_CANCELLABLE",
                         "detail": "Order %d is FILLED; only NEW orders can be cancelled"}
                        """.formatted(orderId));
        assertThat(mvc.get().uri("/orders/{orderId}", orderId))
                .bodyJson().extractingPath("$.status").isEqualTo("FILLED");
    }

    @Test
    void aCancelledOrderCannotBeCancelledAgain() {
        int user = accounts.fundedUser(10_000);
        int orderId = idOf(place("""
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "LIMIT", "size": 1, "price": 900}
                """.formatted(user)));
        cancel(orderId);

        assertThat(cancel(orderId)).hasStatus(HttpStatus.CONFLICT);
    }
}
