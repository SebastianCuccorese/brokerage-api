package dev.cuccorese.brokerage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {"management.server.port=0", "management.prometheus.metrics.export.enabled=true"})
@Import(TestcontainersConfiguration.class)
class HealthProbesTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int apiPort;

    @LocalManagementPort
    private int managementPort;

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void exposesLivenessAndReadinessProbesOnTheManagementPort() throws Exception {
        assertThat(get(managementPort, "/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/health/readiness").statusCode()).isEqualTo(200);
    }

    @Test
    void exposesPrometheusMetricsTaggedWithTheApplicationOnTheManagementPort() throws Exception {
        var response = get(managementPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("jvm_memory_used_bytes", "application=\"brokerage-api\"");
    }

    @Test
    void keepsActuatorOffThePublicPort() throws Exception {
        assertThat(get(apiPort, "/actuator/prometheus").statusCode()).isEqualTo(404);
    }
}
