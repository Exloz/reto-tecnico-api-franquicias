package co.com.pragma.usecase.renamebranchproduct;

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
public class RenameBranchProductUseCase {
    private static final int NAME_MAX_LENGTH = 160;

    private final BranchRepository branchRepository;
    private final BranchProductRepository productRepository;

    public Mono<BranchProduct> execute(
            UUID franchiseId, UUID branchId, UUID productId, String name, long expectedVersion) {
        return Mono.fromCallable(() -> DomainRules.normalizeName(name, NAME_MAX_LENGTH))
                .zipWith(Mono.fromCallable(() -> DomainRules.validateExpectedVersion(expectedVersion)))
                .flatMap(input -> UseCaseSupport.requireResource(
                                () -> branchRepository.findByIdAndFranchiseId(branchId, franchiseId),
                                "Branch",
                                branchId)
                        .then(UseCaseSupport.requireResource(
                                () -> productRepository.findActiveByIdAndBranchId(productId, branchId),
                                "Product",
                                productId))
                        .flatMap(product -> UseCaseSupport.requireVersion(
                                product, product.getVersion(), input.getT2(), "Product", productId))
                        .flatMap(product -> Mono.just(product)
                                .filter(current -> current.getNormalizedName().equals(input.getT1().normalized()))
                                .switchIfEmpty(Mono.defer(() -> productRepository.rename(product.toBuilder()
                                                .name(input.getT1().value())
                                                .normalizedName(input.getT1().normalized())
                                                .version(product.getVersion() + 1)
                                                .updatedAt(Instant.now())
                                                .build(), input.getT2())))));
    }
}
