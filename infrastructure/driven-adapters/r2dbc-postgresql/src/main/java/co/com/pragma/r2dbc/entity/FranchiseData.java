package co.com.pragma.r2dbc.entity;

import co.com.pragma.model.franchises.Franchise;
import io.r2dbc.spi.Row;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FranchiseData(
        UUID id,
        String name,
        String normalizedName,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static FranchiseData from(Row row) {
        return new FranchiseData(
                row.get("id", UUID.class),
                row.get("name", String.class),
                row.get("normalized_name", String.class),
                row.get("version", Long.class),
                row.get("created_at", OffsetDateTime.class).toInstant(),
                row.get("updated_at", OffsetDateTime.class).toInstant());
    }

    public static FranchiseData from(Franchise franchise) {
        return new FranchiseData(
                franchise.getId(),
                franchise.getName(),
                franchise.getNormalizedName(),
                franchise.getVersion(),
                franchise.getCreatedAt(),
                franchise.getUpdatedAt());
    }

    public Franchise toDomain() {
        return Franchise.builder()
                .id(id)
                .name(name)
                .normalizedName(normalizedName)
                .version(version)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
