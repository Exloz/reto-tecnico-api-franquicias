package co.com.pragma.r2dbc;

import co.com.pragma.model.branchproducts.BranchProduct;
import co.com.pragma.model.branchproducts.BranchTopStock;
import co.com.pragma.model.branchproducts.TopStockCursor;
import co.com.pragma.model.branchproducts.gateways.TopStockQueryRepository;
import co.com.pragma.r2dbc.resilience.R2dbcResilience;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TopStockR2dbcAdapter implements TopStockQueryRepository {
    private static final String QUERY = """
            SELECT
                b.id AS branch_id,
                b.name AS branch_name,
                b.normalized_name AS branch_normalized_name,
                p.id AS product_id,
                p.name AS product_name,
                p.normalized_name AS product_normalized_name,
                p.stock AS product_stock,
                p.version AS product_version,
                p.created_at AS product_created_at,
                p.updated_at AS product_updated_at
            FROM franchise.branches b
            LEFT JOIN LATERAL (
                SELECT id, name, normalized_name, stock, version, created_at, updated_at
                FROM franchise.branch_products
                WHERE branch_id = b.id AND deleted_at IS NULL
                ORDER BY stock DESC, id ASC
                LIMIT 1
            ) p ON TRUE
            WHERE b.franchise_id = :franchiseId
            """;
    private static final String ORDER_AND_LIMIT = """
            ORDER BY b.normalized_name ASC, b.id ASC
            LIMIT :limit
            """;

    private final DatabaseClient databaseClient;
    private final R2dbcResilience resilience;

    @Override
    public Flux<BranchTopStock> findTopActiveProductPerBranchOrdered(
            UUID franchiseId, TopStockCursor cursor, int limit) {
        return resilience.readMany(() -> {
            DatabaseClient.GenericExecuteSpec statement = Optional.ofNullable(cursor)
                    .map(this::queryAfter)
                    .orElseGet(() -> databaseClient.sql(QUERY + ORDER_AND_LIMIT));
            return statement
                    .bind("franchiseId", franchiseId)
                    .bind("limit", limit)
                    .map((row, metadata) -> mapRow(row))
                    .all();
        });
    }

    private DatabaseClient.GenericExecuteSpec queryAfter(TopStockCursor cursor) {
        return databaseClient.sql(QUERY + """
                        AND (b.normalized_name, b.id) > (:cursorName, :cursorId)
                        """ + ORDER_AND_LIMIT)
                .bind("cursorName", cursor.branchNormalizedName())
                .bind("cursorId", cursor.branchId());
    }

    private BranchTopStock mapRow(Row row) {
        UUID branchId = row.get("branch_id", UUID.class);
        BranchProduct product = Optional.ofNullable(row.get("product_id", UUID.class))
                .map(productId -> BranchProduct.builder()
                        .id(productId)
                        .branchId(branchId)
                        .name(row.get("product_name", String.class))
                        .normalizedName(row.get("product_normalized_name", String.class))
                        .stock(row.get("product_stock", Integer.class))
                        .version(row.get("product_version", Long.class))
                        .createdAt(row.get("product_created_at", OffsetDateTime.class).toInstant())
                        .updatedAt(row.get("product_updated_at", OffsetDateTime.class).toInstant())
                        .build())
                .orElse(null);
        return new BranchTopStock(
                branchId,
                row.get("branch_name", String.class),
                row.get("branch_normalized_name", String.class),
                product);
    }
}
