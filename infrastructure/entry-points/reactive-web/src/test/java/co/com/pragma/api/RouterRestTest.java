package co.com.pragma.api;

import co.com.pragma.api.config.JsonConfig;
import co.com.pragma.api.error.GlobalExceptionHandler;
import co.com.pragma.api.filter.CorrelationIdFilter;
import co.com.pragma.api.pagination.CursorCodec;
import co.com.pragma.model.branches.Branch;
import co.com.pragma.model.branchproducts.BranchProduct;
import co.com.pragma.model.branchproducts.BranchTopStock;
import co.com.pragma.model.branchproducts.TopStockCursor;
import co.com.pragma.model.branchproducts.TopStockPage;
import co.com.pragma.model.common.exception.InvalidNameException;
import co.com.pragma.model.common.exception.DuplicateNameException;
import co.com.pragma.model.common.exception.ResourceNotFoundException;
import co.com.pragma.model.common.exception.VersionConflictException;
import co.com.pragma.model.franchises.Franchise;
import co.com.pragma.usecase.addbranch.AddBranchUseCase;
import co.com.pragma.usecase.addbranchproduct.AddBranchProductUseCase;
import co.com.pragma.usecase.createfranchise.CreateFranchiseUseCase;
import co.com.pragma.usecase.deletebranchproduct.DeleteBranchProductUseCase;
import co.com.pragma.usecase.findtopstockproducts.FindTopStockProductsUseCase;
import co.com.pragma.usecase.renamebranch.RenameBranchUseCase;
import co.com.pragma.usecase.renamebranchproduct.RenameBranchProductUseCase;
import co.com.pragma.usecase.renamefranchise.RenameFranchiseUseCase;
import co.com.pragma.usecase.updatebranchproductstock.UpdateBranchProductStockUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {
        RouterRest.class,
        Handler.class,
        CursorCodec.class,
        CorrelationIdFilter.class,
        GlobalExceptionHandler.class,
        JsonConfig.class
})
@WebFluxTest
class RouterRestTest {
    private static final UUID FRANCHISE_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CreateFranchiseUseCase createFranchiseUseCase;
    @MockitoBean
    private AddBranchUseCase addBranchUseCase;
    @MockitoBean
    private AddBranchProductUseCase addBranchProductUseCase;
    @MockitoBean
    private DeleteBranchProductUseCase deleteBranchProductUseCase;
    @MockitoBean
    private UpdateBranchProductStockUseCase updateBranchProductStockUseCase;
    @MockitoBean
    private FindTopStockProductsUseCase findTopStockProductsUseCase;
    @MockitoBean
    private RenameFranchiseUseCase renameFranchiseUseCase;
    @MockitoBean
    private RenameBranchUseCase renameBranchUseCase;
    @MockitoBean
    private RenameBranchProductUseCase renameBranchProductUseCase;

    @Test
    void createsFranchiseWithLocationEtagAndCorrelationId() {
        when(createFranchiseUseCase.execute("Acme")).thenReturn(Mono.just(franchise(0)));

        webTestClient.post()
                .uri("/api/v1/franchises")
                .header(CorrelationIdFilter.HEADER_NAME, "gateway-request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Acme\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals(HttpHeaders.LOCATION, "/api/v1/franchises/" + FRANCHISE_ID)
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"0\"")
                .expectHeader().valueEquals(CorrelationIdFilter.HEADER_NAME, "gateway-request-1")
                .expectBody()
                .jsonPath("$.id").isEqualTo(FRANCHISE_ID.toString())
                .jsonPath("$.name").isEqualTo("Acme")
                .jsonPath("$.normalizedName").doesNotExist();
    }

    @Test
    void exposesCreationRoutes() {
        when(addBranchUseCase.execute(FRANCHISE_ID, "Central")).thenReturn(Mono.just(branch(0)));
        when(addBranchProductUseCase.execute(FRANCHISE_ID, BRANCH_ID, "Phone", null))
                .thenReturn(Mono.just(product(0)));

        webTestClient.post()
                .uri("/api/v1/franchises/{franchiseId}/branches", FRANCHISE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Central\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"0\"")
                .expectBody()
                .jsonPath("$.franchiseId").isEqualTo(FRANCHISE_ID.toString());

        webTestClient.post()
                .uri("/api/v1/franchises/{franchiseId}/branches/{branchId}/products", FRANCHISE_ID, BRANCH_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Phone\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.franchiseId").isEqualTo(FRANCHISE_ID.toString())
                .jsonPath("$.branchId").isEqualTo(BRANCH_ID.toString())
                .jsonPath("$.stock").isEqualTo(5);
    }

    @Test
    void appliesIfMatchToPatchAndDelete() {
        when(updateBranchProductStockUseCase.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID, 9, 2))
                .thenReturn(Mono.just(product(3)));
        when(deleteBranchProductUseCase.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID, 3))
                .thenReturn(Mono.empty());

        webTestClient.patch()
                .uri("/api/v1/franchises/{franchiseId}/branches/{branchId}/products/{productId}/stock",
                        FRANCHISE_ID, BRANCH_ID, PRODUCT_ID)
                .header(HttpHeaders.IF_MATCH, "\"2\"")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"stock\":9}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"3\"")
                .expectBody()
                .jsonPath("$.version").isEqualTo(3);

        webTestClient.delete()
                .uri("/api/v1/franchises/{franchiseId}/branches/{branchId}/products/{productId}",
                        FRANCHISE_ID, BRANCH_ID, PRODUCT_ID)
                .header(HttpHeaders.IF_MATCH, "\"3\"")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void exposesRenameRoutes() {
        when(renameFranchiseUseCase.execute(FRANCHISE_ID, "New", 0)).thenReturn(Mono.just(franchise(1)));
        when(renameBranchUseCase.execute(FRANCHISE_ID, BRANCH_ID, "New", 0)).thenReturn(Mono.just(branch(1)));
        when(renameBranchProductUseCase.execute(FRANCHISE_ID, BRANCH_ID, PRODUCT_ID, "New", 0))
                .thenReturn(Mono.just(product(1)));

        rename("/api/v1/franchises/" + FRANCHISE_ID)
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ETAG, "\"1\"");
        rename("/api/v1/franchises/" + FRANCHISE_ID + "/branches/" + BRANCH_ID)
                .expectStatus().isOk();
        rename("/api/v1/franchises/" + FRANCHISE_ID + "/branches/" + BRANCH_ID
                + "/products/" + PRODUCT_ID)
                .expectStatus().isOk();
    }

    @Test
    void returnsPagedTopStockWithOpaqueCursor() {
        TopStockCursor nextCursor = new TopStockCursor("central", BRANCH_ID);
        BranchTopStock item = new BranchTopStock(BRANCH_ID, "Central", "central", product(1));
        when(findTopStockProductsUseCase.execute(FRANCHISE_ID, null, 50))
                .thenReturn(Mono.just(new TopStockPage(List.of(item), nextCursor)));

        webTestClient.get()
                .uri("/api/v1/franchises/{franchiseId}/branches/top-stock-products", FRANCHISE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].branchId").isEqualTo(BRANCH_ID.toString())
                .jsonPath("$.items[0].product.id").isEqualTo(PRODUCT_ID.toString())
                .jsonPath("$.items[0].product.name").isEqualTo("Phone")
                .jsonPath("$.items[0].product.stock").isEqualTo(5)
                .jsonPath("$.items[0].product.franchiseId").doesNotExist()
                .jsonPath("$.nextCursor").isNotEmpty();
    }

    @Test
    void rejectsMissingAndStalePreconditions() {
        when(renameFranchiseUseCase.execute(eq(FRANCHISE_ID), eq("New"), anyLong()))
                .thenReturn(Mono.error(new VersionConflictException("Franchise", FRANCHISE_ID, 0, 1)));

        webTestClient.patch()
                .uri("/api/v1/franchises/{franchiseId}", FRANCHISE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"New\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.PRECONDITION_REQUIRED)
                .expectHeader().contentType("application/problem+json")
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:franchise-api:problem:precondition-required");

        rename("/api/v1/franchises/" + FRANCHISE_ID)
                .expectStatus().isEqualTo(HttpStatus.PRECONDITION_FAILED)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:franchise-api:problem:version-conflict");
    }

    @Test
    void rejectsUnknownJsonAndMapsDomainValidation() {
        webTestClient.post()
                .uri("/api/v1/franchises")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Acme\",\"unexpected\":true}")
                .exchange()
                .expectStatus().isBadRequest();

        when(createFranchiseUseCase.execute(" ")).thenReturn(Mono.error(new InvalidNameException("Name is blank")));

        webTestClient.post()
                .uri("/api/v1/franchises")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\" \"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:franchise-api:problem:invalid-name")
                .jsonPath("$.detail").isEqualTo("Name is blank");
    }

    @Test
    void mapsNotFoundConflictAndInternalErrors() {
        when(createFranchiseUseCase.execute("Duplicate"))
                .thenReturn(Mono.error(new DuplicateNameException("Franchise", "Duplicate")));
        when(createFranchiseUseCase.execute("Failure"))
                .thenReturn(Mono.error(new RuntimeException("database password")));
        when(renameBranchUseCase.execute(FRANCHISE_ID, BRANCH_ID, "Missing", 0))
                .thenReturn(Mono.error(new ResourceNotFoundException("Branch", BRANCH_ID)));

        webTestClient.post()
                .uri("/api/v1/franchises")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Duplicate\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:franchise-api:problem:duplicate-name");

        webTestClient.patch()
                .uri("/api/v1/franchises/{franchiseId}/branches/{branchId}", FRANCHISE_ID, BRANCH_ID)
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Missing\"}")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:franchise-api:problem:resource-not-found");

        webTestClient.post()
                .uri("/api/v1/franchises")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Failure\"}")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:franchise-api:problem:internal-error")
                .jsonPath("$.detail").isEqualTo("An unexpected error occurred");
    }

    @Test
    void appliesProblemContractToUnknownRoutes() {
        webTestClient.get()
                .uri("/api/v1/unknown")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType("application/problem+json")
                .expectHeader().exists(CorrelationIdFilter.HEADER_NAME)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:franchise-api:problem:http-404");
    }

    @Test
    void rejectsInvalidPreconditionsAndPagination() {
        webTestClient.patch()
                .uri("/api/v1/franchises/{franchiseId}", FRANCHISE_ID)
                .header(HttpHeaders.IF_MATCH, "\"0\"", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"New\"}")
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient.patch()
                .uri("/api/v1/franchises/{franchiseId}", FRANCHISE_ID)
                .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"New\"}")
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient.get()
                .uri("/api/v1/franchises/{franchiseId}/branches/top-stock-products?limit=101", FRANCHISE_ID)
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient.get()
                .uri("/api/v1/franchises/{franchiseId}/branches/top-stock-products?cursor=invalid", FRANCHISE_ID)
                .exchange()
                .expectStatus().isBadRequest();
    }

    private WebTestClient.ResponseSpec rename(String uri) {
        return webTestClient.patch()
                .uri(uri)
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"New\"}")
                .exchange();
    }

    private Franchise franchise(long version) {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        return Franchise.builder()
                .id(FRANCHISE_ID)
                .name("Acme")
                .normalizedName("acme")
                .version(version)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Branch branch(long version) {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        return Branch.builder()
                .id(BRANCH_ID)
                .franchiseId(FRANCHISE_ID)
                .name("Central")
                .normalizedName("central")
                .version(version)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private BranchProduct product(long version) {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        return BranchProduct.builder()
                .id(PRODUCT_ID)
                .branchId(BRANCH_ID)
                .name("Phone")
                .normalizedName("phone")
                .stock(5)
                .version(version)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
