package dev.cuccorese.brokerage.trading;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cuccorese.brokerage.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Runs against the provided seed; the expected numbers are worked out in the README. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PortfolioTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void valuesTheSeedUsersPortfolio() {
        assertThat(mvc.get().uri("/users/1/portfolio")).hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {
                          "userId": 1,
                          "totalValue": {"currency": "ARS", "amount": 889756.00},
                          "availableCash": {"currency": "ARS", "amount": 627500.00},
                          "positions": [
                            {"instrumentId": 31, "ticker": "BMA", "name": "Banco Macro S.A.", "quantity": -10,
                             "marketValue": {"currency": "ARS", "amount": -15028.00},
                             "totalReturnPct": 1.78, "dailyReturnPct": -1.15},
                            {"instrumentId": 54, "ticker": "METR", "name": "MetroGAS S.A.", "quantity": 500,
                             "marketValue": {"currency": "ARS", "amount": 114750.00},
                             "totalReturnPct": -8.20, "dailyReturnPct": -1.08},
                            {"instrumentId": 47, "ticker": "PAMP", "name": "Pampa Holding S.A.", "quantity": 40,
                             "marketValue": {"currency": "ARS", "amount": 37034.00},
                             "totalReturnPct": -0.45, "dailyReturnPct": 0.44}
                          ]
                        }
                        """);
    }

    @Test
    void aUserWithoutOrdersHasAnEmptyPortfolio() {
        assertThat(mvc.get().uri("/users/2/portfolio")).hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"userId": 2,
                         "totalValue": {"currency": "ARS", "amount": 0.00},
                         "availableCash": {"currency": "ARS", "amount": 0.00},
                         "positions": []}
                        """);
    }

    @Test
    void anUnknownUserIsNotFound() {
        assertThat(mvc.get().uri("/users/999/portfolio")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").isEqualTo("User 999 not found");
    }
}
