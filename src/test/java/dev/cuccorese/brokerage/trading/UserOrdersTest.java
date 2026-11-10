package dev.cuccorese.brokerage.trading;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cuccorese.brokerage.FixedClockConfiguration;
import dev.cuccorese.brokerage.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Lists the orders of users created per test; PAMP (47) last closed at 925.85 and the fixed clock gives every placed order the same second. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class UserOrdersTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransferService transfers;

    private TestAccounts accounts;

    @BeforeEach
    void setUp() {
        accounts = new TestAccounts(jdbc, transfers);
    }

    private void place(int user, String side, String type, String extra) {
        assertThat(mvc.post().uri("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"userId": %d, "instrumentId": 47, "side": "%s", "type": "%s", %s}
                """.formatted(user, side, type, extra))).hasStatus(HttpStatus.CREATED);
    }

    private MockMvcTester.MockMvcRequestBuilder orders(int user) {
        return mvc.get().uri("/users/{userId}/orders", user);
    }

    /** A FILLED buy, a NEW limit buy and a REJECTED sell, all at the fixed 2026 time; funding is a transfer, not an order. */
    private int userWithOneOrderOfEachStatus() {
        int user = accounts.fundedUser(10_000);
        place(user, "BUY", "MARKET", "\"size\": 1");
        place(user, "BUY", "LIMIT", "\"size\": 1, \"price\": 900");
        place(user, "SELL", "MARKET", "\"size\": 1000");
        return user;
    }

    @Test
    void listsNewestFirstBreakingTiesByDescendingId() {
        int user = userWithOneOrderOfEachStatus();

        assertThat(orders(user)).hasStatusOk().bodyJson()
                .extractingPath("$.content[*].status").asArray()
                .containsExactly("REJECTED", "NEW", "FILLED");
        assertThat(orders(user)).bodyJson().extractingPath("$.content[*].side").asArray()
                .containsExactly("SELL", "BUY", "BUY");
    }

    @Test
    void usesTheSameViewAsASingleOrder() {
        int user = accounts.fundedUser(10_000);
        place(user, "BUY", "MARKET", "\"size\": 10");

        var body = assertThat(orders(user)).hasStatusOk().bodyJson();
        body.extractingPath("$.content[0].userId").isEqualTo(user);
        body.extractingPath("$.content[0].price.amount").isEqualTo(925.85);
        body.extractingPath("$.content[0].datetime").isEqualTo("2026-11-10T14:30:00Z");
        body.extractingPath("$.content[0].rejectionReason").isNull();
    }

    @Test
    void pagesWithTheTotalsOfTheWholeListing() {
        int user = userWithOneOrderOfEachStatus();

        assertThat(orders(user).param("size", "2")).bodyJson().isLenientlyEqualTo("""
                {"content": [{"status": "REJECTED"}, {"status": "NEW"}],
                 "page": {"size": 2, "number": 0, "totalElements": 3, "totalPages": 2}}
                """);
        assertThat(orders(user).param("size", "2").param("page", "1")).bodyJson().isLenientlyEqualTo("""
                {"content": [{"status": "FILLED"}],
                 "page": {"size": 2, "number": 1, "totalElements": 3, "totalPages": 2}}
                """);
    }

    @Test
    void filtersByStatus() {
        int user = userWithOneOrderOfEachStatus();

        assertThat(orders(user).param("status", "FILLED")).bodyJson().isLenientlyEqualTo("""
                {"content": [{"side": "BUY"}], "page": {"totalElements": 1}}
                """);
        assertThat(orders(user).param("status", "NEW")).bodyJson()
                .extractingPath("$.page.totalElements").isEqualTo(1);
        assertThat(orders(user).param("status", "CANCELLED")).bodyJson()
                .extractingPath("$.content").asArray().isEmpty();
    }

    @Test
    void capsThePageSizeAtOneHundred() {
        assertThat(orders(accounts.newUser()).param("size", "1000")).bodyJson()
                .extractingPath("$.page.size").isEqualTo(100);
    }

    @Test
    void aUserWithoutOrdersGetsAnEmptyPage() {
        assertThat(orders(accounts.newUser())).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"content": [], "page": {"size": 20, "number": 0, "totalElements": 0, "totalPages": 0}}
                """);
    }

    @Test
    void rejectsAStatusThatDoesNotExist() {
        assertThat(orders(accounts.newUser()).param("status", "PENDING")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anUnknownUserIsNotFound() {
        assertThat(orders(999_999)).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().isLenientlyEqualTo("""
                        {"status": 404, "code": "USER_NOT_FOUND", "detail": "User 999999 not found"}
                        """);
    }
}
