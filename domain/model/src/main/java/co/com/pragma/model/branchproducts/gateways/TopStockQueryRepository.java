package co.com.pragma.model.branchproducts.gateways;

import co.com.pragma.model.branchproducts.BranchTopStock;
import co.com.pragma.model.branchproducts.TopStockCursor;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface TopStockQueryRepository {
    Flux<BranchTopStock> findTopActiveProductPerBranchOrdered(
            UUID franchiseId, TopStockCursor cursor, int limit);
}
