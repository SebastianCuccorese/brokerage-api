package dev.cuccorese.brokerage.market;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cuccorese.brokerage.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
                        {"content": [{"id": 22, "ticker": "BBAR", "name": "Banco Frances", "type": "STOCK"}],
                         "page": {"size": 20, "number": 0, "totalElements": 1, "totalPages": 1}}
                        """);
    }

    @Test
    void findsByNameIgnoringCaseOrderedByTicker() {
        assertThat(search("BANCO")).hasStatusOk()
                .bodyJson().extractingPath("$.content[*].ticker").asArray()
                .containsExactly("BBAR", "BHIP", "BMA", "BPAT");
    }

    @Test
    void neverReturnsTheCashInstrument() {
        assertThat(search("pesos")).hasStatusOk().bodyJson().extractingPath("$.page.totalElements").isEqualTo(0);
    }

    @Test
    void treatsLikeWildcardsAsLiterals() {
        assertThat(search("%")).hasStatusOk().bodyJson().extractingPath("$.page.totalElements").isEqualTo(0);
        assertThat(search("_")).hasStatusOk().bodyJson().extractingPath("$.page.totalElements").isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"compania", "COMPANIA", "compañia", "COMPAÑÍA", "Compañía", "introductora de"})
    void ignoresAccentsAndCaseInTheNameWhateverTheQueryHas(String query) {
        assertThat(search(query)).hasStatusOk()
                .bodyJson().extractingPath("$.content[*].ticker").asArray().containsExactly("INTR");
    }

    @Test
    void anAccentedQueryFindsUnaccentedText() {
        assertThat(search("bánco")).hasStatusOk()
                .bodyJson().extractingPath("$.content[*].ticker").asArray()
                .containsExactly("BBAR", "BHIP", "BMA", "BPAT");
    }

    @Test
    void reportsThePageAndSplitsTheResultsOrderedByTicker() {
        assertThat(search("banco").param("size", "3")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("""
                        {"content": [{"ticker": "BBAR"}, {"ticker": "BHIP"}, {"ticker": "BMA"}],
                         "page": {"size": 3, "number": 0, "totalElements": 4, "totalPages": 2}}
                        """);
        assertThat(search("banco").param("size", "3").param("page", "1")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("""
                        {"content": [{"ticker": "BPAT"}],
                         "page": {"size": 3, "number": 1, "totalElements": 4, "totalPages": 2}}
                        """);
    }

    @Test
    void aPageBeyondTheLastIsEmpty() {
        assertThat(search("banco").param("page", "5")).hasStatusOk().bodyJson()
                .isLenientlyEqualTo("""
                        {"content": [], "page": {"number": 5, "totalElements": 4, "totalPages": 1}}
                        """);
    }

    @Test
    void capsThePageSizeAtOneHundred() {
        assertThat(search("a").param("size", "500")).hasStatusOk()
                .bodyJson().extractingPath("$.page.size").isEqualTo(100);
    }

    @Test
    void ignoresAClientSortSoTheOrderStaysByTicker() {
        assertThat(search("banco").param("sort", "name,desc").param("size", "2")).hasStatusOk()
                .bodyJson().extractingPath("$.content[*].ticker").asArray().containsExactly("BBAR", "BHIP");
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
