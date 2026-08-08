package co.com.pragma.r2dbc;

import co.com.pragma.model.branchproducts.BranchProduct;
import co.com.pragma.model.branchproducts.BranchTopStock;
import co.com.pragma.model.branchproducts.gateways.TopStockQueryRepository;
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
    private final DatabaseClient databaseClient;

    @Override
    public Flux<BranchTopStock> findTopActiveProductPerBranchOrdered(UUID franchiseId) {
        return databaseClient.sql("""
                        SELECT
                            b.id AS branch_id,
                            b.name AS branch_name,
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
                        ORDER BY b.normalized_name ASC, b.id ASC
                        """)
                .bind("franchiseId", franchiseId)
                .map((row, metadata) -> {
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
                    return new BranchTopStock(branchId, row.get("branch_name", String.class), product);
                })
                .all();
    }
}
