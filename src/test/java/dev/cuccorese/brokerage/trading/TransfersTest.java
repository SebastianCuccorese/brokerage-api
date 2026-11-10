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
 * Makes transfers through the API. Every test that writes works on users of its own; the seed
 * user 1 is only read. PAMP (47) last closed at 925.85.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class TransfersTest {

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

    private MvcTestResult transfer(int userId, String json) {
        return mvc.post().uri("/users/{userId}/transfers", userId)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult deposit(int userId, String amount) {
        return transfer(userId, """
                {"type": "DEPOSIT", "amount": %s}
                """.formatted(amount));
    }

    private MvcTestResult withdraw(int userId, String amount) {
        return transfer(userId, """
                {"type": "WITHDRAWAL", "amount": %s}
                """.formatted(amount));
    }

    private MvcTestResult portfolio(int userId) {
        return mvc.get().uri("/users/{userId}/portfolio", userId).exchange();
    }

    private static int idOf(MvcTestResult result) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.id");
    }

    @Test
    void aDepositRaisesTheCashInThePortfolio() {
        int user = accounts.newUser();

        MvcTestResult result = deposit(user, "10000.50");

        assertThat(result).hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "/users/" + user + "/transfers/" + idOf(result))
                .bodyJson().isStrictlyEqualTo("""
                        {"id": %d, "userId": %d, "type": "DEPOSIT", "amount": {"currency": "ARS", "amount": 10000.50},
                         "status": "COMPLETED", "rejectionReason": null, "datetime": "2026-11-10T14:30:00Z"}
                        """.formatted(idOf(result), user));
        assertThat(portfolio(user)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"totalValue": {"currency": "ARS", "amount": 10000.50},
                 "availableCash": {"currency": "ARS", "amount": 10000.50}}
                """);
    }

    @Test
    void aWithdrawalWithinTheCashLowersIt() {
        int user = accounts.fundedUser(10_000);

        assertThat(withdraw(user, "2500")).hasStatus(HttpStatus.CREATED)
                .bodyJson().isLenientlyEqualTo("""
                        {"type": "WITHDRAWAL", "amount": {"currency": "ARS", "amount": 2500.00},
                         "status": "COMPLETED", "rejectionReason": null}
                        """);
        assertThat(portfolio(user)).bodyJson().isLenientlyEqualTo("""
                {"totalValue": {"currency": "ARS", "amount": 7500.00},
                 "availableCash": {"currency": "ARS", "amount": 7500.00}}
                """);
    }

    @Test
    void aWithdrawalBeyondTheAvailableCashIsStoredAsRejected() {
        int user = accounts.fundedUser(1_000);
        mvc.post().uri("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "LIMIT", "size": 1, "price": 600}
                """.formatted(user)).exchange();

        MvcTestResult result = withdraw(user, "500");

        assertThat(result).hasStatus(HttpStatus.CREATED)
                .bodyJson().isLenientlyEqualTo("""
                        {"type": "WITHDRAWAL", "amount": {"currency": "ARS", "amount": 500.00},
                         "status": "REJECTED", "rejectionReason": "INSUFFICIENT_CASH"}
                        """);
        assertThat(mvc.get().uri("/users/{userId}/transfers/{id}", user, idOf(result))).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"status": "REJECTED", "rejectionReason": "INSUFFICIENT_CASH"}
                        """);
        assertThat(portfolio(user)).bodyJson().isLenientlyEqualTo("""
                {"totalValue": {"currency": "ARS", "amount": 1000.00},
                 "availableCash": {"currency": "ARS", "amount": 400.00}}
                """);
        assertThat(withdraw(user, "400")).bodyJson().extractingPath("$.status").isEqualTo("COMPLETED");
    }

    @Test
    void theSeedCashMovementsAreTheSeedUsersTransfersNewestFirst() {
        assertThat(mvc.get().uri("/users/1/transfers")).hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        [{"id": 2, "userId": 1, "type": "WITHDRAWAL", "amount": {"currency": "ARS", "amount": 100000.00},
                          "status": "COMPLETED", "rejectionReason": null, "datetime": "2023-07-13T15:31:20Z"},
                         {"id": 1, "userId": 1, "type": "DEPOSIT", "amount": {"currency": "ARS", "amount": 1000000.00},
                          "status": "COMPLETED", "rejectionReason": null, "datetime": "2023-07-12T15:11:20Z"}]
                        """);
    }

    @Test
    void transfersMadeAtTheSameInstantListTheLatestFirst() {
        int user = accounts.newUser();
        int first = idOf(deposit(user, "100"));
        int second = idOf(withdraw(user, "40"));
        int third = idOf(withdraw(user, "1000"));

        assertThat(mvc.get().uri("/users/{userId}/transfers", user)).hasStatusOk()
                .bodyJson().extractingPath("$[*].id").asArray().containsExactly(third, second, first);
    }

    @Test
    void aUserWithoutTransfersHasAnEmptyHistory() {
        int user = accounts.newUser();

        assertThat(mvc.get().uri("/users/{userId}/transfers", user)).hasStatusOk()
                .bodyJson().isStrictlyEqualTo("[]");
    }

    static Stream<Arguments> invalidTransfers() {
        return Stream.of(
                Arguments.of("malformed JSON", "MALFORMED_REQUEST", """
                        {"type": "DEPOSIT",
                        """),
                Arguments.of("missing type", "VALIDATION_FAILED", """
                        {"amount": 100}
                        """),
                Arguments.of("unknown type", "MALFORMED_REQUEST", """
                        {"type": "CASH_IN", "amount": 100}
                        """),
                Arguments.of("missing amount", "VALIDATION_FAILED", """
                        {"type": "DEPOSIT"}
                        """),
                Arguments.of("zero amount", "VALIDATION_FAILED", """
                        {"type": "DEPOSIT", "amount": 0}
                        """),
                Arguments.of("negative amount", "VALIDATION_FAILED", """
                        {"type": "WITHDRAWAL", "amount": -100}
                        """),
                Arguments.of("amount with three decimals", "VALIDATION_FAILED", """
                        {"type": "DEPOSIT", "amount": 100.001}
                        """),
                Arguments.of("amount wider than its column", "VALIDATION_FAILED", """
                        {"type": "DEPOSIT", "amount": 1234567890123}
                        """),
                Arguments.of("amount that is not a number", "MALFORMED_REQUEST", """
                        {"type": "DEPOSIT", "amount": "lots"}
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTransfers")
    void anInvalidTransferIsABadRequestAndStoresNothing(String description, String code, String json) {
        int user = accounts.newUser();

        assertThat(transfer(user, json)).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().isLenientlyEqualTo("""
                        {"status": 400, "code": "%s"}
                        """.formatted(code));
        assertThat(accounts.transferCount(user)).isZero();
    }

    @Test
    void anUnknownUserIsNotFound() {
        String unknownUser = """
                {"code": "USER_NOT_FOUND", "detail": "User 999999 not found"}
                """;
        assertThat(deposit(999999, "100")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().isLenientlyEqualTo(unknownUser);
        assertThat(mvc.get().uri("/users/999999/transfers")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().isLenientlyEqualTo(unknownUser);
        assertThat(mvc.get().uri("/users/999999/transfers/1")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().isLenientlyEqualTo(unknownUser);
    }

    @Test
    void anUnknownTransferIsNotFound() {
        int user = accounts.newUser();

        assertThat(mvc.get().uri("/users/{userId}/transfers/999999", user)).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().isLenientlyEqualTo("""
                        {"code": "TRANSFER_NOT_FOUND", "detail": "Transfer 999999 not found"}
                        """);
    }

    @Test
    void anotherUsersTransferIsNotFound() {
        int owner = accounts.newUser();
        int transferId = idOf(deposit(owner, "100"));
        int stranger = accounts.newUser();

        assertThat(mvc.get().uri("/users/{userId}/transfers/{id}", stranger, transferId))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().isLenientlyEqualTo("""
                        {"code": "TRANSFER_NOT_FOUND", "detail": "Transfer %d not found"}
                        """.formatted(transferId));
        assertThat(mvc.get().uri("/users/{userId}/transfers/{id}", owner, transferId)).hasStatusOk();
    }
}
