package co.com.pragma.model.branchproducts.gateways;

import co.com.pragma.model.branchproducts.BranchProduct;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface BranchProductRepository {
    Mono<BranchProduct> findActiveByIdAndBranchId(UUID productId, UUID branchId);

    Mono<BranchProduct> create(BranchProduct product);

    Mono<BranchProduct> rename(BranchProduct product, long expectedVersion);

    Mono<BranchProduct> updateStock(BranchProduct product, long expectedVersion);

    Mono<Void> softDelete(BranchProduct product);
}
