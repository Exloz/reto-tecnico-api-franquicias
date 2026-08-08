package co.com.pragma.model.branchproducts;

import java.util.UUID;

public record BranchTopStock(
        UUID branchId,
        String branchName,
        String branchNormalizedName,
        BranchProduct product) {
}
