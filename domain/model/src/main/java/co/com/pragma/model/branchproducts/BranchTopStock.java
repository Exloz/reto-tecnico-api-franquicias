package co.com.pragma.model.branchproducts;

import java.util.UUID;

public record BranchTopStock(UUID branchId, String branchName, BranchProduct product) {
}
