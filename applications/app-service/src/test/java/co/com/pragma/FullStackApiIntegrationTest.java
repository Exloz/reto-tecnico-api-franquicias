package co.com.pragma;

import co.com.pragma.api.dto.ApiResponses;
import co.com.pragma.api.filter.CorrelationIdFilter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FullStackApiIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17.6-alpine")
            .withDatabaseName("franchise")
            .withUsername("franchise_migrator")
            .withPassword("franchise_migrator");

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("adapters.r2dbc.host", POSTGRESQL::getHost);
        registry.add("adapters.r2dbc.port", POSTGRESQL::getFirstMappedPort);
        registry.add("adapters.r2dbc.database", POSTGRESQL::getDatabaseName);
        registry.add("adapters.r2dbc.schema", () -> "franchise");
        registry.add("adapters.r2dbc.username", () -> "franchise_app");
        registry.add("adapters.r2dbc.password", () -> "franchise_app");
        registry.add("adapters.r2dbc.initial-size", () -> 0);
        registry.add("adapters.r2dbc.max-size", () -> 4);
        registry.add("adapters.r2dbc.max-idle-time", () -> "1m");
    }

    @BeforeAll
    static void migrateDatabase() throws SQLException {
        try (Connection connection = migratorConnection()) {
            connection.createStatement().execute("CREATE ROLE franchise_app LOGIN PASSWORD 'franchise_app'");
        }
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .locations("filesystem:" + System.getProperty("migration.path"))
                .load()
                .migrate();
    }

    @BeforeEach
    void cleanDatabase() throws SQLException {
        try (Connection connection = migratorConnection()) {
            connection.createStatement().execute("""
                    TRUNCATE TABLE
                        franchise.branch_products,
                        franchise.branches,
                        franchise.franchises
                    """);
        }
    }

    @Test
    void executesAllVersionOneOperationsAgainstPostgresql() {
        EntityExchangeResult<ApiResponses.Franchise> franchiseResult = webTestClient.post()
                .uri("/api/v1/franchises")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Acme\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"0\"")
                .expectBody(ApiResponses.Franchise.class)
                .returnResult();
        UUID franchiseId = Objects.requireNonNull(franchiseResult.getResponseBody()).id();

        EntityExchangeResult<ApiResponses.Branch> branchResult = webTestClient.post()
                .uri("/api/v1/franchises/{franchiseId}/branches", franchiseId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Central\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"0\"")
                .expectBody(ApiResponses.Branch.class)
                .returnResult();
        UUID branchId = Objects.requireNonNull(branchResult.getResponseBody()).id();

        EntityExchangeResult<ApiResponses.Product> productResult = webTestClient.post()
                .uri("/api/v1/franchises/{franchiseId}/branches/{branchId}/products", franchiseId, branchId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Phone\",\"stock\":5}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"0\"")
                .expectBody(ApiResponses.Product.class)
                .returnResult();
        UUID productId = Objects.requireNonNull(productResult.getResponseBody()).id();

        rename("/api/v1/franchises/" + franchiseId, "Acme North", 0)
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"1\"");
        rename("/api/v1/franchises/" + franchiseId + "/branches/" + branchId, "North", 0)
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"1\"");
        rename("/api/v1/franchises/" + franchiseId + "/branches/" + branchId
                + "/products/" + productId, "Tablet", 0)
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"1\"");

        webTestClient.patch()
                .uri("/api/v1/franchises/{franchiseId}/branches/{branchId}/products/{productId}/stock",
                        franchiseId, branchId, productId)
                .header(HttpHeaders.IF_MATCH, "\"1\"")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"stock\":9}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"2\"")
                .expectBody()
                .jsonPath("$.stock").isEqualTo(9);

        webTestClient.get()
                .uri("/api/v1/franchises/{franchiseId}/branches/top-stock-products?limit=1", franchiseId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].branchName").isEqualTo("North")
                .jsonPath("$.items[0].product.name").isEqualTo("Tablet")
                .jsonPath("$.items[0].product.stock").isEqualTo(9);

        webTestClient.delete()
                .uri("/api/v1/franchises/{franchiseId}/branches/{branchId}/products/{productId}",
                        franchiseId, branchId, productId)
                .header(HttpHeaders.IF_MATCH, "\"2\"")
                .exchange()
                .expectStatus().isNoContent();

        EntityExchangeResult<ApiResponses.TopStockPage> topStockResult = webTestClient.get()
                .uri("/api/v1/franchises/{franchiseId}/branches/top-stock-products", franchiseId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ApiResponses.TopStockPage.class)
                .returnResult();
        assertNull(Objects.requireNonNull(topStockResult.getResponseBody()).items().getFirst().product());
    }

    @Test
    void mapsDatabaseAndRequestFailuresToTheHttpContract() {
        EntityExchangeResult<ApiResponses.Franchise> created = webTestClient.post()
                .uri("/api/v1/franchises")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Ácme\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ApiResponses.Franchise.class)
                .returnResult();
        UUID franchiseId = Objects.requireNonNull(created.getResponseBody()).id();

        webTestClient.post()
                .uri("/api/v1/franchises")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"ACME\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectHeader().contentType("application/problem+json")
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:franchise-api:problem:duplicate-name");

        rename("/api/v1/franchises/" + franchiseId, "First", 0)
                .expectStatus().isOk();
        rename("/api/v1/franchises/" + franchiseId, "Stale", 0)
                .expectStatus().isEqualTo(HttpStatus.PRECONDITION_FAILED);

        webTestClient.patch()
                .uri("/api/v1/franchises/{franchiseId}", franchiseId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Missing precondition\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.PRECONDITION_REQUIRED);

        webTestClient.post()
                .uri("/api/v1/franchises")
                .header(CorrelationIdFilter.HEADER_NAME, "full-stack-request")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().valueEquals(CorrelationIdFilter.HEADER_NAME, "full-stack-request")
                .expectBody()
                .jsonPath("$.correlationId").isEqualTo("full-stack-request");
    }

    private WebTestClient.ResponseSpec rename(String uri, String name, long version) {
        return webTestClient.patch()
                .uri(uri)
                .header(HttpHeaders.IF_MATCH, "\"" + version + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"" + name + "\"}")
                .exchange();
    }

    private static Connection migratorConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
    }
}
