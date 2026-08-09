package co.com.pragma.config;

import co.com.pragma.api.filter.CorrelationIdFilter;
import co.com.pragma.api.filter.RequestObservabilityFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AutoConfigureWebTestClient
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "adapters.r2dbc.host=localhost",
                "adapters.r2dbc.port=1",
                "adapters.r2dbc.database=franchise",
                "adapters.r2dbc.schema=franchise",
                "adapters.r2dbc.username=franchise_app",
                "adapters.r2dbc.password=franchise_app",
                "adapters.r2dbc.ssl-mode=DISABLE",
                "adapters.r2dbc.initial-size=0",
                "adapters.r2dbc.max-size=2",
                "adapters.r2dbc.max-idle-time=1m"
        })
class SwaggerDefaultProfileTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void hidesSwaggerUiAndWebjarAssets() {
        webTestClient.get()
                .uri("/swagger-ui.html")
                .exchange()
                .expectStatus().isNotFound();

        webTestClient.get()
                .uri("/webjars/swagger-ui/5.32.11/swagger-ui-bundle.js")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType("application/problem+json")
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:franchise-api:problem:http-404");
    }

    @Test
    void keepsCorsDisabledOutsideLocalProfile() {
        webTestClient.options()
                .uri("/api/v1/franchises")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .exchange()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void keepsLivenessIndependentFromDatabaseReadiness() {
        webTestClient.get()
                .uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");

        webTestClient.get()
                .uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.status").isEqualTo("DOWN");
    }

    @Test
    void writesCorrelatedStructuredLogsWithoutRequestBodies(CapturedOutput output) {
        webTestClient.post()
                .uri("/api/v1/franchises")
                .header(CorrelationIdFilter.HEADER_NAME, "correlation-123")
                .header(RequestObservabilityFilter.API_GATEWAY_REQUEST_ID_HEADER, "gateway-456")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"never-log-this-value\"")
                .exchange()
                .expectStatus().isBadRequest();

        String logs = output.getAll();
        assertTrue(logs.contains("\"event\":\"request.completed\""));
        assertTrue(logs.contains("\"correlationId\":\"correlation-123\""));
        assertTrue(logs.contains("\"apiGatewayRequestId\":\"gateway-456\""));
        assertTrue(logs.contains("\"route\":\"/api/v1/franchises\""));
        assertTrue(logs.contains("\"operation\":\"CreateFranchiseUseCase\""));
        assertTrue(logs.contains("\"status\":400"));
        assertTrue(logs.contains("\"outcome\":\"client_error\""));
        assertTrue(logs.contains("\"errorCode\":\"http-400\""));
        assertFalse(logs.contains("never-log-this-value"));
    }

    @Test
    void publishesHttpLatencyDistributions() {
        webTestClient.get()
                .uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk();

        String metrics = webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertTrue(metrics.contains("http_server_requests_seconds"));
        assertTrue(metrics.contains("http_server_requests_seconds_bucket"));
        assertTrue(metrics.contains("application=\"ApiFranchise\""));
        assertTrue(metrics.contains("environment=\"local\""));
        assertTrue(metrics.contains("r2dbc_pool_acquired_connections"));
        assertTrue(metrics.contains("r2dbc_pool_idle_connections"));
        assertTrue(metrics.contains("r2dbc_pool_pending_connections"));
    }
}
