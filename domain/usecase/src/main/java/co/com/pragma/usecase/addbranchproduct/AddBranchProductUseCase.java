package co.com.pragma.usecase.addbranchproduct;

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
public class AddBranchProductUseCase {
    private static final int NAME_MAX_LENGTH = 160;

    private final BranchRepository branchRepository;
    private final BranchProductRepository productRepository;

    public Mono<BranchProduct> execute(UUID franchiseId, UUID branchId, String name, Integer stock) {
        return Mono.fromCallable(() -> DomainRules.normalizeName(name, NAME_MAX_LENGTH))
                .zipWith(Mono.fromCallable(() -> DomainRules.initialStock(stock)))
                .flatMap(input -> UseCaseSupport.requireResource(
                                () -> branchRepository.findByIdAndFranchiseId(branchId, franchiseId),
                                "Branch",
                                branchId)
                        .then(Mono.defer(() -> {
                            Instant now = Instant.now();
                            return productRepository.create(BranchProduct.builder()
                                    .id(UUID.randomUUID())
                                    .branchId(branchId)
                                    .name(input.getT1().value())
                                    .normalizedName(input.getT1().normalized())
                                    .stock(input.getT2())
                                    .version(0)
                                    .createdAt(now)
                                    .updatedAt(now)
                                    .build());
                        })));
    }
}
