package co.com.pragma.usecase.deletebranchproduct;

import co.com.pragma.model.branchproducts.gateways.BranchProductRepository;
import co.com.pragma.model.branches.gateways.BranchRepository;
import co.com.pragma.usecase.common.UseCaseSupport;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class DeleteBranchProductUseCase {
    private final BranchRepository branchRepository;
    private final BranchProductRepository productRepository;

    public Mono<Void> execute(UUID franchiseId, UUID branchId, UUID productId) {
        return UseCaseSupport.requireResource(
                        () -> branchRepository.findByIdAndFranchiseId(branchId, franchiseId), "Branch", branchId)
                .then(UseCaseSupport.requireResource(
                        () -> productRepository.findActiveByIdAndBranchId(productId, branchId),
                        "Product",
                        productId))
                .flatMap(product -> Mono.defer(() -> {
                    Instant now = Instant.now();
                    return productRepository.softDelete(product.toBuilder()
                            .version(product.getVersion() + 1)
                            .updatedAt(now)
                            .deletedAt(now)
                            .build());
                }));
    }
}
