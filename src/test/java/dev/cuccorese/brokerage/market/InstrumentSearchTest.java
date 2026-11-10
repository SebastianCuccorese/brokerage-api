package dev.cuccorese.brokerage.market;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cuccorese.brokerage.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InstrumentSearchTest {

    @Autowired
    private MockMvcTester mvc;

    private MockMvcTester.MockMvcRequestBuilder search(String query) {
        return mvc.get().uri("/instruments").param("query", query);
    }

    @Test
    void findsByTicker() {
        assertThat(search("bbar")).hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [{"id": 22, "ticker": "BBAR", "name": "Banco Frances", "type": "ACCIONES"}]
                        """);
    }

    @Test
    void findsByNameIgnoringCaseOrderedByTicker() {
        assertThat(search("BANCO")).hasStatusOk()
                .bodyJson().extractingPath("$[*].ticker").asArray()
                .containsExactly("BBAR", "BHIP", "BMA", "BPAT");
    }

    @Test
    void neverReturnsTheCashInstrument() {
        assertThat(search("pesos")).hasStatusOk().bodyJson().isStrictlyEqualTo("[]");
    }

    @Test
    void treatsLikeWildcardsAsLiterals() {
        assertThat(search("%")).hasStatusOk().bodyJson().isStrictlyEqualTo("[]");
        assertThat(search("_")).hasStatusOk().bodyJson().isStrictlyEqualTo("[]");
    }

    @Test
    void rejectsABlankQuery() {
        assertThat(search(" ")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.status").isEqualTo(400);
    }

    @Test
    void rejectsAMissingQuery() {
        assertThat(mvc.get().uri("/instruments")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
