package co.com.pragma.r2dbc.entity;

import co.com.pragma.model.branchproducts.BranchProduct;
import io.r2dbc.spi.Row;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public record BranchProductData(
        UUID id,
        UUID branchId,
        String name,
        String normalizedName,
        int stock,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt) {

    public static BranchProductData from(Row row) {
        return new BranchProductData(
                row.get("id", UUID.class),
                row.get("branch_id", UUID.class),
                row.get("name", String.class),
                row.get("normalized_name", String.class),
                row.get("stock", Integer.class),
                row.get("version", Long.class),
                row.get("created_at", OffsetDateTime.class).toInstant(),
                row.get("updated_at", OffsetDateTime.class).toInstant(),
                Optional.ofNullable(row.get("deleted_at", OffsetDateTime.class))
                        .map(OffsetDateTime::toInstant)
                        .orElse(null));
    }

    public static BranchProductData from(BranchProduct product) {
        return new BranchProductData(
                product.getId(),
                product.getBranchId(),
                product.getName(),
                product.getNormalizedName(),
                product.getStock(),
                product.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getDeletedAt());
    }

    public BranchProduct toDomain() {
        return BranchProduct.builder()
                .id(id)
                .branchId(branchId)
                .name(name)
                .normalizedName(normalizedName)
                .stock(stock)
                .version(version)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .deletedAt(deletedAt)
                .build();
    }
}
