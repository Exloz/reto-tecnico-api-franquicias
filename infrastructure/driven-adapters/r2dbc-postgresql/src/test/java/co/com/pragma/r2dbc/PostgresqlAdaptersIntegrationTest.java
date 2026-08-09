package co.com.pragma.r2dbc;

import co.com.pragma.model.branches.Branch;
import co.com.pragma.model.branchproducts.BranchProduct;
import co.com.pragma.model.branchproducts.TopStockCursor;
import co.com.pragma.model.common.exception.DuplicateNameException;
import co.com.pragma.model.common.exception.InvalidStockException;
import co.com.pragma.model.common.exception.ResourceNotFoundException;
import co.com.pragma.model.common.exception.VersionConflictException;
import co.com.pragma.model.franchises.Franchise;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers(disabledWithoutDocker = true)
class PostgresqlAdaptersIntegrationTest {
    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17.6-alpine")
            .withDatabaseName("franchise")
            .withUsername("franchise_migrator")
            .withPassword("franchise_migrator");

    private static ConnectionPool connectionPool;
    private static DatabaseClient databaseClient;
    private static FranchiseR2dbcAdapter franchiseAdapter;
    private static BranchR2dbcAdapter branchAdapter;
    private static BranchProductR2dbcAdapter productAdapter;
    private static TopStockR2dbcAdapter topStockAdapter;

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())) {
            connection.createStatement().execute("CREATE ROLE franchise_app LOGIN PASSWORD 'franchise_app'");
        }
        Flyway.configure()
                .dataSource(POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())
                .locations("filesystem:" + System.getProperty("migration.path"))
                .load()
                .migrate();

        PostgresqlConnectionConfiguration connectionConfiguration = PostgresqlConnectionConfiguration.builder()
                .host(POSTGRESQL.getHost())
                .port(POSTGRESQL.getFirstMappedPort())
                .database(POSTGRESQL.getDatabaseName())
                .schema("franchise")
                .username("franchise_app")
                .password("franchise_app")
                .build();
        connectionPool = new ConnectionPool(ConnectionPoolConfiguration.builder()
                .connectionFactory(new PostgresqlConnectionFactory(connectionConfiguration))
                .initialSize(0)
                .maxSize(8)
                .build());
        databaseClient = DatabaseClient.create(connectionPool);
        TransactionalOperator transactionalOperator = TransactionalOperator.create(
                new R2dbcTransactionManager(connectionPool));
        franchiseAdapter = new FranchiseR2dbcAdapter(databaseClient, transactionalOperator);
        branchAdapter = new BranchR2dbcAdapter(databaseClient, transactionalOperator);
        productAdapter = new BranchProductR2dbcAdapter(databaseClient, transactionalOperator);
        topStockAdapter = new TopStockR2dbcAdapter(databaseClient);
    }

    @AfterAll
    static void disposePool() {
        connectionPool.dispose();
    }

    @BeforeEach
    void cleanDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())) {
            connection.createStatement().execute("""
                    TRUNCATE TABLE
                        franchise.branch_products,
                        franchise.branches,
                        franchise.franchises
                    """);
        }
    }

    @Test
    void enforcesNormalizedNameUniqueness() {
        Franchise first = franchise(UUID.randomUUID(), "Ácme", "acme");
        Franchise duplicate = franchise(UUID.randomUUID(), "ACME", "acme");

        StepVerifier.create(franchiseAdapter.create(first).then(franchiseAdapter.create(duplicate)))
                .expectError(DuplicateNameException.class)
                .verify();
    }

    @Test
    void serializesConcurrentDuplicateNamesAtEveryScope() {
        Franchise firstFranchise = franchise(UUID.randomUUID(), "Ácme", "acme");
        Franchise secondFranchise = franchise(UUID.randomUUID(), "ACME", "acme");

        StepVerifier.create(Flux.mergeDelayError(
                                2,
                                franchiseAdapter.create(firstFranchise),
                                franchiseAdapter.create(secondFranchise))
                        .materialize()
                        .collectList())
                .assertNext(signals -> {
                    assertEquals(2, signals.size());
                    assertEquals(1, signals.stream().filter(s -> s.isOnNext()
                            && s.get().getNormalizedName().equals("acme")).count());
                    assertEquals(1, signals.stream().filter(s -> s.isOnError()
                            && s.getThrowable() instanceof DuplicateNameException).count());
                })
                .verifyComplete();

        Franchise branchParent = franchise(UUID.randomUUID(), "Branch Parent", "branch parent");
        Branch firstBranch = branch(UUID.randomUUID(), branchParent.getId(), "Central", "central");
        Branch secondBranch = branch(UUID.randomUUID(), branchParent.getId(), "CENTRAL", "central");
        StepVerifier.create(franchiseAdapter.create(branchParent).thenMany(Flux.mergeDelayError(
                                        2,
                                        branchAdapter.create(firstBranch),
                                        branchAdapter.create(secondBranch))
                                .materialize()
                                .collectList()))
                .assertNext(signals -> {
                    assertEquals(2, signals.size());
                    assertEquals(1, signals.stream().filter(s -> s.isOnNext()
                            && s.get().getNormalizedName().equals("central")).count());
                    assertEquals(1, signals.stream().filter(s -> s.isOnError()
                            && s.getThrowable() instanceof DuplicateNameException).count());
                })
                .verifyComplete();

        Branch productParent = branch(UUID.randomUUID(), branchParent.getId(), "Product Parent", "product parent");
        BranchProduct firstProduct = product(UUID.randomUUID(), productParent.getId(), "Phone", "phone", 1);
        BranchProduct secondProduct = product(UUID.randomUUID(), productParent.getId(), "PHONE", "phone", 2);
        StepVerifier.create(branchAdapter.create(productParent).thenMany(Flux.mergeDelayError(
                                        2,
                                        productAdapter.create(firstProduct),
                                        productAdapter.create(secondProduct))
                                .materialize()
                                .collectList()))
                .assertNext(signals -> {
                    assertEquals(2, signals.size());
                    assertEquals(1, signals.stream().filter(s -> s.isOnNext()
                            && s.get().getNormalizedName().equals("phone")).count());
                    assertEquals(1, signals.stream().filter(s -> s.isOnError()
                            && s.getThrowable() instanceof DuplicateNameException).count());
                })
                .verifyComplete();
    }

    @Test
    void detectsConcurrentVersionUpdates() {
        Franchise franchise = franchise(UUID.randomUUID(), "Acme", "acme");
        Franchise firstRename = franchise.toBuilder()
                .name("First")
                .normalizedName("first")
                .version(1)
                .updatedAt(Instant.now())
                .build();
        Franchise staleRename = franchise.toBuilder()
                .name("Stale")
                .normalizedName("stale")
                .version(1)
                .updatedAt(Instant.now())
                .build();

        StepVerifier.create(franchiseAdapter.create(franchise)
                        .then(franchiseAdapter.rename(firstRename, 0))
                        .then(franchiseAdapter.rename(staleRename, 0)))
                .expectError(VersionConflictException.class)
                .verify();
    }

    @Test
    void translatesRelationalIntegrityErrors() {
        Franchise franchise = franchise(UUID.randomUUID(), "Acme", "acme");
        Branch branch = branch(UUID.randomUUID(), franchise.getId(), "Central", "central");
        Branch duplicateBranch = branch(UUID.randomUUID(), franchise.getId(), "CENTRAL", "central");
        Branch orphanBranch = branch(UUID.randomUUID(), UUID.randomUUID(), "Orphan", "orphan");
        BranchProduct invalidProduct = product(UUID.randomUUID(), branch.getId(), "Invalid", "invalid", -1);

        StepVerifier.create(franchiseAdapter.create(franchise).then(branchAdapter.create(branch)))
                .expectNextCount(1)
                .verifyComplete();

        StepVerifier.create(branchAdapter.create(duplicateBranch))
                .expectError(DuplicateNameException.class)
                .verify();

        StepVerifier.create(branchAdapter.create(orphanBranch))
                .expectError(ResourceNotFoundException.class)
                .verify();

        StepVerifier.create(productAdapter.create(invalidProduct))
                .expectError(InvalidStockException.class)
                .verify();
    }

    @Test
    void renamesBranchesAndProductsWithAtomicVersionChecks() {
        Franchise franchise = franchise(UUID.randomUUID(), "Acme", "acme");
        Branch branch = branch(UUID.randomUUID(), franchise.getId(), "Central", "central");
        Branch occupiedBranch = branch(UUID.randomUUID(), franchise.getId(), "Occupied", "occupied");
        BranchProduct product = product(UUID.randomUUID(), branch.getId(), "Phone", "phone", 5);
        BranchProduct occupiedProduct = product(UUID.randomUUID(), branch.getId(), "Occupied", "occupied", 7);
        Branch renamedBranch = branch.toBuilder()
                .name("North")
                .normalizedName("north")
                .version(1)
                .updatedAt(Instant.now())
                .build();
        BranchProduct renamedProduct = product.toBuilder()
                .name("Tablet")
                .normalizedName("tablet")
                .version(1)
                .updatedAt(Instant.now())
                .build();

        StepVerifier.create(franchiseAdapter.create(franchise)
                        .then(branchAdapter.create(branch))
                        .then(branchAdapter.create(occupiedBranch))
                        .then(productAdapter.create(product))
                        .then(productAdapter.create(occupiedProduct))
                        .then(branchAdapter.rename(renamedBranch, 0)))
                .assertNext(renamed -> {
                    assertEquals("North", renamed.getName());
                    assertEquals(1, renamed.getVersion());
                })
                .verifyComplete();

        StepVerifier.create(productAdapter.rename(renamedProduct, 0))
                .assertNext(renamed -> {
                    assertEquals("Tablet", renamed.getName());
                    assertEquals(1, renamed.getVersion());
                })
                .verifyComplete();

        StepVerifier.create(branchAdapter.rename(renamedBranch.toBuilder()
                        .name("Occupied")
                        .normalizedName("occupied")
                        .build(), 1))
                .expectError(DuplicateNameException.class)
                .verify();

        StepVerifier.create(productAdapter.rename(renamedProduct.toBuilder()
                        .name("Occupied")
                        .normalizedName("occupied")
                        .build(), 1))
                .expectError(DuplicateNameException.class)
                .verify();

        StepVerifier.create(branchAdapter.rename(renamedBranch, 0))
                .expectError(VersionConflictException.class)
                .verify();

        StepVerifier.create(productAdapter.rename(renamedProduct, 0))
                .expectError(VersionConflictException.class)
                .verify();

        Branch missingBranch = branch(UUID.randomUUID(), franchise.getId(), "Missing", "missing");
        BranchProduct missingProduct = product(UUID.randomUUID(), branch.getId(), "Missing", "missing", 0);

        StepVerifier.create(branchAdapter.rename(missingBranch, 0))
                .expectError(ResourceNotFoundException.class)
                .verify();

        StepVerifier.create(productAdapter.rename(missingProduct, 0))
                .expectError(ResourceNotFoundException.class)
                .verify();
    }

    @Test
    void deniesSchemaChangesToApplicationRole() {
        StepVerifier.create(databaseClient.sql("CREATE TABLE franchise.forbidden_operation (id UUID)")
                        .fetch()
                        .rowsUpdated())
                .expectErrorMatches(error -> PostgresqlErrorMapper.hasSqlState(error, "42501"))
                .verify();
    }

    @Test
    void serializesConcurrentStockUpdates() {
        Franchise franchise = franchise(UUID.randomUUID(), "Acme", "acme");
        Branch branch = branch(UUID.randomUUID(), franchise.getId(), "Central", "central");
        BranchProduct product = product(UUID.randomUUID(), branch.getId(), "Phone", "phone", 1);
        BranchProduct firstUpdate = product.toBuilder()
                .stock(10)
                .version(1)
                .updatedAt(Instant.now())
                .build();
        BranchProduct secondUpdate = product.toBuilder()
                .stock(20)
                .version(1)
                .updatedAt(Instant.now())
                .build();

        Mono<Void> setup = franchiseAdapter.create(franchise)
                .then(branchAdapter.create(branch))
                .then(productAdapter.create(product))
                .then();

        StepVerifier.create(setup.thenMany(Flux.mergeDelayError(
                                2,
                                productAdapter.updateStock(firstUpdate, 0),
                                productAdapter.updateStock(secondUpdate, 0)))
                        .materialize()
                        .collectList())
                .assertNext(signals -> {
                    assertEquals(2, signals.size());
                    long onSuccess = signals.stream().filter(s -> s.isOnNext() && s.get().getVersion() == 1).count();
                    long onError = signals.stream().filter(s -> s.isOnError()
                            && s.getThrowable() instanceof VersionConflictException).count();
                    assertEquals(1, onSuccess);
                    assertEquals(1, onError);
                })
                .verifyComplete();
    }

    @Test
    void serializesConcurrentUpdateAndDelete() {
        Franchise franchise = franchise(UUID.randomUUID(), "Acme", "acme");
        Branch branch = branch(UUID.randomUUID(), franchise.getId(), "Central", "central");
        BranchProduct product = product(UUID.randomUUID(), branch.getId(), "Phone", "phone", 1);
        Instant updateTime = Instant.now();
        BranchProduct update = product.toBuilder()
                .stock(10)
                .version(1)
                .updatedAt(updateTime)
                .build();
        BranchProduct deletion = product.toBuilder()
                .version(1)
                .updatedAt(updateTime)
                .deletedAt(updateTime)
                .build();

        Mono<Void> setup = franchiseAdapter.create(franchise)
                .then(branchAdapter.create(branch))
                .then(productAdapter.create(product))
                .then();

        StepVerifier.create(setup.thenMany(Flux.mergeDelayError(
                                2,
                                productAdapter.updateStock(update, 0).thenReturn("update"),
                                productAdapter.softDelete(deletion, 0).thenReturn("delete")))
                        .materialize()
                        .collectList())
                .assertNext(signals -> {
                    assertEquals(2, signals.size());
                    long onSuccess = signals.stream().filter(s -> s.isOnNext()
                            && (s.get().equals("update") || s.get().equals("delete"))).count();
                    long onError = signals.stream().filter(s -> s.isOnError()
                            && s.getThrowable() instanceof VersionConflictException).count();
                    assertEquals(1, onSuccess);
                    assertEquals(1, onError);
                })
                .verifyComplete();
    }

    @Test
    void softDeleteReleasesNameAndRejectsStaleDelete() {
        Franchise franchise = franchise(UUID.randomUUID(), "Acme", "acme");
        Branch branch = branch(UUID.randomUUID(), franchise.getId(), "Central", "central");
        BranchProduct product = product(UUID.randomUUID(), branch.getId(), "Phone", "phone", 5);
        BranchProduct stockUpdate = product.toBuilder()
                .stock(6)
                .version(1)
                .updatedAt(Instant.now())
                .build();
        Instant staleDeletionTime = Instant.now();
        BranchProduct staleDelete = product.toBuilder()
                .version(1)
                .updatedAt(staleDeletionTime)
                .deletedAt(staleDeletionTime)
                .build();

        StepVerifier.create(franchiseAdapter.create(franchise)
                        .then(branchAdapter.create(branch))
                        .then(productAdapter.create(product))
                        .then(productAdapter.updateStock(stockUpdate, 0)))
                .expectNextMatches(updated -> updated.getVersion() == 1 && updated.getStock() == 6)
                .verifyComplete();

        StepVerifier.create(productAdapter.softDelete(staleDelete, 0))
                .expectError(VersionConflictException.class)
                .verify();

        Instant deletionTime = Instant.now();
        BranchProduct deleted = stockUpdate.toBuilder()
                .version(2)
                .updatedAt(deletionTime)
                .deletedAt(deletionTime)
                .build();
        BranchProduct replacement = product(UUID.randomUUID(), branch.getId(), "PHONE", "phone", 7);

        StepVerifier.create(productAdapter.softDelete(deleted, 1).then(productAdapter.create(replacement)))
                .expectNextMatches(created -> created.getId().equals(replacement.getId()))
                .verifyComplete();

        StepVerifier.create(productAdapter.softDelete(deleted, 1))
                .expectError(VersionConflictException.class)
                .verify();
    }

    @Test
    void returnsTopActiveProductForEveryOrderedBranch() {
        Franchise franchise = franchise(UUID.randomUUID(), "Acme", "acme");
        Branch alpha = branch(UUID.randomUUID(), franchise.getId(), "Álpha", "alpha");
        Branch zeta = branch(UUID.randomUUID(), franchise.getId(), "Zeta", "zeta");
        BranchProduct higherId = product(
                UUID.fromString("00000000-0000-0000-0000-000000000002"), alpha.getId(), "Second", "second", 10);
        BranchProduct lowerId = product(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), alpha.getId(), "First", "first", 10);
        BranchProduct removed = product(UUID.randomUUID(), alpha.getId(), "Removed", "removed", 99);
        Instant deletionTime = Instant.now();

        Mono<Void> setup = franchiseAdapter.create(franchise)
                .then(branchAdapter.create(zeta))
                .then(branchAdapter.create(alpha))
                .then(productAdapter.create(higherId))
                .then(productAdapter.create(lowerId))
                .then(productAdapter.create(removed))
                .flatMap(saved -> productAdapter.softDelete(saved.toBuilder()
                        .version(1)
                        .updatedAt(deletionTime)
                        .deletedAt(deletionTime)
                        .build(), 0));

        StepVerifier.create(
                        setup.thenMany(topStockAdapter.findTopActiveProductPerBranchOrdered(
                                franchise.getId(), null, 3)),
                        0)
                .thenRequest(1)
                .assertNext(result -> {
                    assertEquals(alpha.getId(), result.branchId());
                    assertEquals(lowerId.getId(), result.product().getId());
                })
                .thenRequest(1)
                .assertNext(result -> {
                    assertEquals(zeta.getId(), result.branchId());
                    assertNull(result.product());
                })
                .verifyComplete();

        StepVerifier.create(topStockAdapter.findTopActiveProductPerBranchOrdered(
                        franchise.getId(), new TopStockCursor("alpha", alpha.getId()), 2))
                .assertNext(result -> assertEquals(zeta.getId(), result.branchId()))
                .verifyComplete();
    }

    private static Franchise franchise(UUID id, String name, String normalizedName) {
        Instant now = Instant.now();
        return Franchise.builder()
                .id(id)
                .name(name)
                .normalizedName(normalizedName)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static Branch branch(UUID id, UUID franchiseId, String name, String normalizedName) {
        Instant now = Instant.now();
        return Branch.builder()
                .id(id)
                .franchiseId(franchiseId)
                .name(name)
                .normalizedName(normalizedName)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static BranchProduct product(UUID id, UUID branchId, String name, String normalizedName, int stock) {
        Instant now = Instant.now();
        return BranchProduct.builder()
                .id(id)
                .branchId(branchId)
                .name(name)
                .normalizedName(normalizedName)
                .stock(stock)
                .version(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
