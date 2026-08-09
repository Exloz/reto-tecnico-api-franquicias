package co.com.pragma.api.config;

import co.com.pragma.api.filter.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.DefaultCorsProcessor;
import org.springframework.web.cors.reactive.CorsWebFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@WebFluxTest(properties = {
        "cors.enabled=true",
        "cors.allowed-origins=http://localhost:4200"
})
@ContextConfiguration(classes = {CorsConfig.class, SecurityHeadersConfig.class, CorrelationIdFilter.class})
class ConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CorsConfiguration corsConfiguration;

    @Autowired
    private List<CorsWebFilter> corsWebFilters;

    @Test
    void appliesSecurityHeadersToResponses() {
        webTestClient.get()
                .uri("/missing")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().valueEquals("Content-Security-Policy",
                        "default-src 'self'; frame-ancestors 'self'; form-action 'self'")
                .expectHeader().valueEquals("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload")
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("Server", "")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().valueEquals("Pragma", "no-cache")
                .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin");
    }

    @Test
    void allowsConfiguredCorsMethodsAndExposesContractHeaders() {
        assertThat(corsConfiguration.checkOrigin("http://localhost:4200"))
                .isEqualTo("http://localhost:4200");
        assertThat(corsConfiguration.checkHttpMethod(HttpMethod.PATCH)).contains(HttpMethod.PATCH);
        assertThat(corsConfiguration.checkHeaders(List.of("Content-Type", "If-Match")))
                .containsExactly("Content-Type", "If-Match");
        assertThat(corsWebFilters).hasSize(1);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.options(
                        "http://localhost/api/v1/franchises")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,If-Match")
                .build());
        assertThat(new DefaultCorsProcessor().process(corsConfiguration, exchange)).isTrue();

        webTestClient.options()
                .uri("http://localhost/api/v1/franchises")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,If-Match")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(CorrelationIdFilter.HEADER_NAME)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200")
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        value -> org.assertj.core.api.Assertions.assertThat(value).contains("PATCH"))
                .expectHeader().value(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        value -> org.assertj.core.api.Assertions.assertThat(value)
                                .contains("ETag", "Location", "X-Correlation-ID"));
    }
}
