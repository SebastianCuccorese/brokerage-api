package dev.cuccorese.brokerage.shared;

import static org.assertj.core.api.Assertions.assertThat;

import dev.cuccorese.brokerage.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RequestIdTest {

    @Autowired
    private MockMvcTester mvc;

    private String requestIdFor(String incoming) {
        MockMvcTester.MockMvcRequestBuilder request = mvc.get().uri("/instruments").param("query", "bbar");
        if (incoming != null) {
            request = request.header(RequestIdFilter.HEADER, incoming);
        }
        return request.exchange().getResponse().getHeader(RequestIdFilter.HEADER);
    }

    @Test
    void generatesAnIdWhenNoneIsSent() {
        assertThat(UUID.fromString(requestIdFor(null))).isNotNull();
    }

    @Test
    void echoesAValidIncomingId() {
        assertThat(requestIdFor("client-42.a_b")).isEqualTo("client-42.a_b");
    }

    static Stream<String> unsafeIds() {
        return Stream.of("bad\nline", "bad id", "x".repeat(65), "x".repeat(200));
    }

    @ParameterizedTest
    @MethodSource("unsafeIds")
    void replacesAnUnsafeIncomingId(String unsafe) {
        String id = requestIdFor(unsafe);

        assertThat(id).isNotEqualTo(unsafe);
        assertThat(UUID.fromString(id)).isNotNull();
    }
}
