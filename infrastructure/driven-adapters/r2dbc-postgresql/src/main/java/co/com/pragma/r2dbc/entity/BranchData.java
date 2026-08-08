package co.com.pragma.r2dbc.entity;

import co.com.pragma.model.branches.Branch;
import io.r2dbc.spi.Row;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BranchData(
        UUID id,
        UUID franchiseId,
        String name,
        String normalizedName,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static BranchData from(Row row) {
        return new BranchData(
                row.get("id", UUID.class),
                row.get("franchise_id", UUID.class),
                row.get("name", String.class),
                row.get("normalized_name", String.class),
                row.get("version", Long.class),
                row.get("created_at", OffsetDateTime.class).toInstant(),
                row.get("updated_at", OffsetDateTime.class).toInstant());
    }

    public static BranchData from(Branch branch) {
        return new BranchData(
                branch.getId(),
                branch.getFranchiseId(),
                branch.getName(),
                branch.getNormalizedName(),
                branch.getVersion(),
                branch.getCreatedAt(),
                branch.getUpdatedAt());
    }

    public Branch toDomain() {
        return Branch.builder()
                .id(id)
                .franchiseId(franchiseId)
                .name(name)
                .normalizedName(normalizedName)
                .version(version)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
