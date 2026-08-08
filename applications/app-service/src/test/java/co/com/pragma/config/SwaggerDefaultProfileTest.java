package co.com.pragma.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "adapters.r2dbc.host=localhost",
                "adapters.r2dbc.port=5432",
                "adapters.r2dbc.database=franchise",
                "adapters.r2dbc.schema=franchise",
                "adapters.r2dbc.username=franchise_app",
                "adapters.r2dbc.password=franchise_app",
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
}
