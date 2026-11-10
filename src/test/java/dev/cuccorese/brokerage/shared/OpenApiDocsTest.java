package dev.cuccorese.brokerage.shared;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cuccorese.brokerage.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiDocsTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void publishesTheSpecWithTitleAndInstrumentSearch() {
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk()
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.info.title").isEqualTo("brokerage-api");
                    assertThat(json).extractingPath("$.info.version").asString().doesNotContain("@");
                    assertThat(json).extractingPath("$.paths").asMap().containsKey("/instruments");
                });
    }
}
