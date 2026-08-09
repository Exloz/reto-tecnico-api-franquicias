package co.com.pragma.usecase.updatebranchproductstock;

import co.com.pragma.model.branchproducts.BranchProduct;
import co.com.pragma.model.branchproducts.gateways.BranchProductRepository;
import co.com.pragma.model.branches.gateways.BranchRepository;
import co.com.pragma.model.common.DomainRules;
import co.com.pragma.usecase.common.UseCaseSupport;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class UpdateBranchProductStockUseCase {
    private final BranchRepository branchRepository;
    private final BranchProductRepository productRepository;

    public Mono<BranchProduct> execute(
            UUID franchiseId, UUID branchId, UUID productId, int stock, long expectedVersion) {
        return Mono.fromCallable(() -> DomainRules.validateStock(stock))
                .zipWith(Mono.fromCallable(() -> DomainRules.validateExpectedVersion(expectedVersion)))
                .flatMap(validated -> UseCaseSupport.requireResource(
                                () -> branchRepository.findByIdAndFranchiseId(branchId, franchiseId),
                                "Branch",
                                branchId)
                        .then(UseCaseSupport.requireResource(
                                () -> productRepository.findActiveByIdAndBranchId(productId, branchId),
                                "Product",
                                productId))
                        .flatMap(product -> UseCaseSupport.requireVersion(
                                product,
                                product.getVersion(),
                                validated.getT2(),
                                "Product",
                                productId))
                        .flatMap(product -> productRepository.updateStock(product.toBuilder()
                                .stock(validated.getT1())
                                .version(product.getVersion() + 1)
                                .updatedAt(Instant.now())
                                .build(), validated.getT2())));
    }
}
