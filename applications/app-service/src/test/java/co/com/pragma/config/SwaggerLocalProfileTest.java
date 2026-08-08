package co.com.pragma.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

@ActiveProfiles("local")
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
class SwaggerLocalProfileTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void exposesProjectContractAndSwaggerUi() {
        webTestClient.get()
                .uri("/swagger-ui.html")
                .exchange()
                .expectStatus().isTemporaryRedirect()
                .expectHeader().valueEquals("Location", "/swagger-ui/index.html");

        webTestClient.get()
                .uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body).contains("Franchise API"));

        webTestClient.get()
                .uri("/swagger-ui/assets/swagger-ui.css")
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body).isNotEmpty());

        WebTestClient.ResponseSpec bundleResponse = webTestClient.get()
                .uri("/swagger-ui/assets/swagger-ui-bundle.js")
                .exchange()
                .expectStatus().isOk();
        StepVerifier.create(bundleResponse.returnResult(DataBuffer.class).getResponseBody())
                .thenConsumeWhile(buffer -> {
                    DataBufferUtils.release(buffer);
                    return true;
                })
                .verifyComplete();

        webTestClient.get()
                .uri("/openapi/franchise-api.yaml")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> org.assertj.core.api.Assertions.assertThat(body)
                        .contains("openapi: 3.1.0", "operationId: createFranchise"));
    }
}
