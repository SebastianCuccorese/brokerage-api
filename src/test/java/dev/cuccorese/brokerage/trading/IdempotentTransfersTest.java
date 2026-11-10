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

/** Retries of {@code POST /users/{userId}/transfers} with an {@code Idempotency-Key}. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
class IdempotentTransfersTest {

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

    private MvcTestResult transfer(int userId, String key, String type, String amount) {
        var request = mvc.post().uri("/users/{userId}/transfers", userId).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "%s", "amount": %s}
                        """.formatted(type, amount));
        return (key == null ? request : request.header(IdempotencyKey.HEADER, key)).exchange();
    }

    private static int idOf(MvcTestResult result) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.id");
    }

    @Test
    void theSameKeyTwiceMakesOneTransferAndReplaysIt() {
        int user = accounts.newUser();

        MvcTestResult first = transfer(user, "in-1", "CASH_IN", "1000");
        MvcTestResult retry = transfer(user, "in-1", "CASH_IN", "1000");

        assertThat(first).hasStatus(HttpStatus.CREATED).doesNotContainHeader(IdempotencyKey.REPLAYED_HEADER);
        assertThat(retry).hasStatus(HttpStatus.CREATED)
                .hasHeader(IdempotencyKey.REPLAYED_HEADER, "true")
                .hasHeader("Location", "/users/" + user + "/transfers/" + idOf(first))
                .bodyJson().isLenientlyEqualTo("""
                        {"id": %d, "type": "CASH_IN", "status": "COMPLETED", "amount": {"amount": 1000.00}}
                        """.formatted(idOf(first)));
        assertThat(accounts.transferCount(user)).isEqualTo(1);
    }

    @Test
    void theSameKeyWithADifferentAmountIsRefusedAndStoresNothing() {
        int user = accounts.newUser();
        transfer(user, "in-2", "CASH_IN", "1000");

        assertThat(transfer(user, "in-2", "CASH_IN", "2000")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(accounts.transferCount(user)).isEqualTo(1);
    }

    @Test
    void theScaleOfTheAmountDoesNotMakeADifferentTransfer() {
        int user = accounts.newUser();
        int transferId = idOf(transfer(user, "in-3", "CASH_IN", "100"));

        assertThat(transfer(user, "in-3", "CASH_IN", "100.00"))
                .hasHeader(IdempotencyKey.REPLAYED_HEADER, "true")
                .bodyJson().extractingPath("$.id").isEqualTo(transferId);
    }

    /** The deposit in between would let the withdrawal through now: the replay must not decide it again. */
    @Test
    void aRejectedWithdrawalReplaysAsRejected() {
        int user = accounts.fundedUser(1_000);
        int transferId = idOf(transfer(user, "out-1", "CASH_OUT", "5000"));
        accounts.deposit(user, 10_000);

        assertThat(transfer(user, "out-1", "CASH_OUT", "5000")).hasStatus(HttpStatus.CREATED)
                .hasHeader(IdempotencyKey.REPLAYED_HEADER, "true")
                .bodyJson().isLenientlyEqualTo("""
                        {"id": %d, "status": "REJECTED", "rejectionReason": "INSUFFICIENT_CASH"}
                        """.formatted(transferId));
        assertThat(accounts.transferCount(user)).isEqualTo(3);
    }

    @Test
    void anotherUsersKeyDoesNotCollide() {
        int alice = accounts.newUser();
        int bob = accounts.newUser();
        transfer(alice, "shared-key", "CASH_IN", "1000");

        assertThat(transfer(bob, "shared-key", "CASH_IN", "1000")).hasStatus(HttpStatus.CREATED)
                .doesNotContainHeader(IdempotencyKey.REPLAYED_HEADER);
        assertThat(accounts.transferCount(bob)).isEqualTo(1);
    }

    @Test
    void theSameKeyForAnOrderAndATransferDoesNotCollide() {
        int user = accounts.fundedUser(10_000);
        mvc.post().uri("/orders").contentType(MediaType.APPLICATION_JSON).header(IdempotencyKey.HEADER, "both")
                .content("""
                        {"userId": %d, "instrumentId": 47, "side": "BUY", "type": "MARKET", "size": 1}
                        """.formatted(user))
                .exchange();

        assertThat(transfer(user, "both", "CASH_IN", "1000")).hasStatus(HttpStatus.CREATED)
                .doesNotContainHeader(IdempotencyKey.REPLAYED_HEADER);
        assertThat(accounts.transferCount(user)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "with space", "a/b"})
    void anInvalidKeyIsABadRequestAndStoresNothing(String key) {
        int user = accounts.newUser();

        assertThat(transfer(user, key, "CASH_IN", "1000")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().isLenientlyEqualTo("""
                        {"code": "VALIDATION_FAILED", "errors": [{"field": "Idempotency-Key"}]}
                        """);
        assertThat(accounts.transferCount(user)).isZero();
    }

    @Test
    void withoutAKeyEveryRequestMakesATransfer() {
        int user = accounts.newUser();

        transfer(user, null, "CASH_IN", "1000");

        assertThat(transfer(user, null, "CASH_IN", "1000")).hasStatus(HttpStatus.CREATED)
                .doesNotContainHeader(IdempotencyKey.REPLAYED_HEADER);
        assertThat(accounts.transferCount(user)).isEqualTo(2);
    }
}
