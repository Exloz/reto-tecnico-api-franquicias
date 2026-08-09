package co.com.pragma.r2dbc;

import co.com.pragma.model.branchproducts.BranchProduct;
import co.com.pragma.model.branchproducts.gateways.BranchProductRepository;
import co.com.pragma.model.common.exception.DuplicateNameException;
import co.com.pragma.model.common.exception.InvalidStockException;
import co.com.pragma.model.common.exception.ResourceNotFoundException;
import co.com.pragma.model.common.exception.VersionConflictException;
import co.com.pragma.r2dbc.entity.BranchProductData;
import co.com.pragma.r2dbc.resilience.R2dbcResilience;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.UnaryOperator;

@Repository
@RequiredArgsConstructor
public class BranchProductR2dbcAdapter implements BranchProductRepository {
    private static final String COLUMNS = """
            id, branch_id, name, normalized_name, stock, version, created_at, updated_at, deleted_at
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;
    private final R2dbcResilience resilience;

    @Override
    public Mono<BranchProduct> findActiveByIdAndBranchId(UUID productId, UUID branchId) {
        return resilience.read(() -> databaseClient.sql("SELECT " + COLUMNS + " FROM franchise.branch_products "
                        + "WHERE id = :id AND branch_id = :branchId AND deleted_at IS NULL")
                .bind("id", productId)
                .bind("branchId", branchId)
                .map((row, metadata) -> BranchProductData.from(row).toDomain())
                .one());
    }

    @Override
    public Mono<BranchProduct> create(BranchProduct product) {
        BranchProductData data = BranchProductData.from(product);
        return resilience.write(() -> databaseClient.sql("""
                        INSERT INTO franchise.branch_products
                            (id, branch_id, name, normalized_name, stock, version, created_at, updated_at)
                        VALUES (:id, :branchId, :name, :normalizedName, :stock, :version, :createdAt, :updatedAt)
                        RETURNING
                        """ + COLUMNS)
                .bind("id", data.id())
                .bind("branchId", data.branchId())
                .bind("name", data.name())
                .bind("normalizedName", data.normalizedName())
                .bind("stock", data.stock())
                .bind("version", data.version())
                .bind("createdAt", OffsetDateTime.ofInstant(data.createdAt(), ZoneOffset.UTC))
                .bind("updatedAt", OffsetDateTime.ofInstant(data.updatedAt(), ZoneOffset.UTC))
                .map((row, metadata) -> BranchProductData.from(row).toDomain())
                .one()
                .onErrorMap(
                        error -> PostgresqlErrorMapper.hasConstraint(
                                error, "uq_branch_products_active_normalized_name"),
                        error -> new DuplicateNameException("Product", data.name()))
                .onErrorMap(
                        error -> PostgresqlErrorMapper.hasSqlState(error, "23503"),
                        error -> new ResourceNotFoundException("Branch", data.branchId()))
                .onErrorMap(
                        error -> PostgresqlErrorMapper.hasSqlState(error, "23514"),
                        error -> new InvalidStockException(data.stock())));
    }

    @Override
    public Mono<BranchProduct> rename(BranchProduct product, long expectedVersion) {
        BranchProductData data = BranchProductData.from(product);
        return resilience.write(() -> update(data, expectedVersion, """
                SET name = :name,
                    normalized_name = :normalizedName,
                    version = version + 1,
                    updated_at = :updatedAt
                """, statement -> statement
                        .bind("name", data.name())
                        .bind("normalizedName", data.normalizedName()))
                .onErrorMap(
                        error -> PostgresqlErrorMapper.hasConstraint(
                                error, "uq_branch_products_active_normalized_name"),
                        error -> new DuplicateNameException("Product", data.name())));
    }

    @Override
    public Mono<BranchProduct> updateStock(BranchProduct product, long expectedVersion) {
        BranchProductData data = BranchProductData.from(product);
        return resilience.write(() -> update(data, expectedVersion, """
                SET stock = :stock,
                    version = version + 1,
                    updated_at = :updatedAt
                """, statement -> statement.bind("stock", data.stock()))
                .onErrorMap(
                        error -> PostgresqlErrorMapper.hasSqlState(error, "23514"),
                        error -> new InvalidStockException(data.stock())));
    }

    @Override
    public Mono<Void> softDelete(BranchProduct product, long expectedVersion) {
        BranchProductData data = BranchProductData.from(product);
        return resilience.write(() -> databaseClient.sql("""
                        UPDATE franchise.branch_products
                        SET deleted_at = :deletedAt,
                            version = version + 1,
                            updated_at = :updatedAt
                        WHERE id = :id
                          AND branch_id = :branchId
                          AND version = :expectedVersion
                          AND deleted_at IS NULL
                        RETURNING
                        """ + COLUMNS)
                .bind("deletedAt", OffsetDateTime.ofInstant(data.deletedAt(), ZoneOffset.UTC))
                .bind("updatedAt", OffsetDateTime.ofInstant(data.updatedAt(), ZoneOffset.UTC))
                .bind("id", data.id())
                .bind("branchId", data.branchId())
                .bind("expectedVersion", expectedVersion)
                .map((row, metadata) -> BranchProductData.from(row).toDomain())
                .one()
                .switchIfEmpty(Mono.defer(() -> diagnoseUpdate(data, expectedVersion)))
                .then()
                .as(transactionalOperator::transactional));
    }

    private Mono<BranchProduct> update(
            BranchProductData data,
            long expectedVersion,
            String assignment,
            UnaryOperator<DatabaseClient.GenericExecuteSpec> bindChanges) {
        DatabaseClient.GenericExecuteSpec statement = databaseClient.sql("UPDATE franchise.branch_products "
                        + assignment + " WHERE id = :id AND branch_id = :branchId "
                        + "AND version = :expectedVersion AND deleted_at IS NULL RETURNING " + COLUMNS)
                .bind("updatedAt", OffsetDateTime.ofInstant(data.updatedAt(), ZoneOffset.UTC))
                .bind("id", data.id())
                .bind("branchId", data.branchId())
                .bind("expectedVersion", expectedVersion);
        return bindChanges.apply(statement)
                .map((row, metadata) -> BranchProductData.from(row).toDomain())
                .one()
                .switchIfEmpty(Mono.defer(() -> diagnoseUpdate(data, expectedVersion)))
                .as(transactionalOperator::transactional);
    }

    private Mono<BranchProduct> diagnoseUpdate(BranchProductData data, long expectedVersion) {
        return databaseClient.sql("SELECT " + COLUMNS + " FROM franchise.branch_products "
                        + "WHERE id = :id AND branch_id = :branchId")
                .bind("id", data.id())
                .bind("branchId", data.branchId())
                .map((row, metadata) -> BranchProductData.from(row).toDomain())
                .one()
                .flatMap(current -> Mono.<BranchProduct>error(new VersionConflictException(
                        "Product", data.id(), expectedVersion, current.getVersion())))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Product", data.id())));
    }
}
