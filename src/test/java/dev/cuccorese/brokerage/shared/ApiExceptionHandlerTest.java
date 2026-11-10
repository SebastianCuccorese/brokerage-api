package dev.cuccorese.brokerage.shared;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cuccorese.brokerage.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ApiExceptionHandlerTest.Failing.class})
class ApiExceptionHandlerTest {

    private static final String SECRET = "secret detail";

    /** Raises each kind of error on demand, so the advice is tested without depending on a real rule. */
    @RestController
    static class Failing {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new NotFoundException(ErrorCode.ORDER_NOT_FOUND, "Order", 7);
        }

        @GetMapping("/test/invalid")
        void invalid() {
            throw new InvalidRequestException(ErrorCode.INSTRUMENT_NOT_TRADABLE, "ARS is cash");
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new ConflictException(ErrorCode.ORDER_NOT_CANCELLABLE, "Order 7 is FILLED");
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException(SECRET);
        }
    }

    @Autowired
    private MockMvcTester mvc;

    private MvcTestResult get(String path) {
        return mvc.get().uri(path).header(RequestIdFilter.HEADER, "req-1").exchange();
    }

    @Test
    void notFoundIs404WithItsCode() {
        assertThat(get("/test/not-found")).hasStatus(HttpStatus.NOT_FOUND).bodyJson()
                .isLenientlyEqualTo("""
                        {"status": 404, "title": "Not Found", "detail": "Order 7 not found",
                         "code": "ORDER_NOT_FOUND", "requestId": "req-1"}
                        """);
    }

    @Test
    void invalidRequestIs400WithItsCode() {
        assertThat(get("/test/invalid")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .isLenientlyEqualTo("""
                        {"status": 400, "title": "Bad Request", "detail": "ARS is cash",
                         "code": "INSTRUMENT_NOT_TRADABLE", "requestId": "req-1"}
                        """);
    }

    @Test
    void conflictIs409WithItsCode() {
        assertThat(get("/test/conflict")).hasStatus(HttpStatus.CONFLICT).bodyJson()
                .isLenientlyEqualTo("""
                        {"status": 409, "title": "Conflict", "detail": "Order 7 is FILLED",
                         "code": "ORDER_NOT_CANCELLABLE", "requestId": "req-1"}
                        """);
    }

    @Test
    void anUnexpectedErrorIs500WithoutItsMessage() throws Exception {
        MvcTestResult result = get("/test/unexpected");

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR).bodyJson()
                .isLenientlyEqualTo("""
                        {"status": 500, "code": "INTERNAL_ERROR", "requestId": "req-1",
                         "detail": "Unexpected error; quote the request id"}
                        """);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(SECRET);
    }

    @Test
    void aValidationFailureNamesTheField() {
        MvcTestResult result = mvc.post().uri("/orders").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userId": 1, "instrumentId": 47, "side": "BUY", "type": "MARKET", "size": -3}
                        """)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .isLenientlyEqualTo("""
                        {"code": "VALIDATION_FAILED", "errors": [{"field": "size", "message": "must be greater than 0"}]}
                        """);
        assertThat(result).bodyJson().extractingPath("$.requestId").isNotNull();
    }

    @Test
    void malformedJsonDoesNotEchoJacksonInternals() throws Exception {
        MvcTestResult result = mvc.post().uri("/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": ")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.code").isEqualTo("MALFORMED_REQUEST");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Jackson", "JSON parse", "Unexpected end");
    }

    @Test
    void aFrameworkErrorKeepsItsStatusAndGainsTheRequestId() {
        MvcTestResult result = mvc.post().uri("/instruments").header(RequestIdFilter.HEADER, "req-1").exchange();

        assertThat(result).hasStatus(HttpStatus.METHOD_NOT_ALLOWED).bodyJson()
                .extractingPath("$.requestId").isEqualTo("req-1");
    }
}
