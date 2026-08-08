package co.com.pragma.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiResponses {

    private ApiResponses() {
    }

    public record Franchise(
            UUID id,
            String name,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record Branch(
            UUID id,
            UUID franchiseId,
            String name,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record Product(
            UUID id,
            UUID franchiseId,
            UUID branchId,
            String name,
            int stock,
            long version,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record TopStockProduct(UUID id, String name, int stock) {
    }

    public record BranchTopStock(UUID branchId, String branchName, TopStockProduct product) {
    }

    public record TopStockPage(List<BranchTopStock> items, String nextCursor) {
    }
}
