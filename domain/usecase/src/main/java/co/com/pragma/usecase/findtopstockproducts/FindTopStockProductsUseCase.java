package co.com.pragma.usecase.findtopstockproducts;

import co.com.pragma.model.branchproducts.BranchTopStock;
import co.com.pragma.model.branchproducts.gateways.TopStockQueryRepository;
import co.com.pragma.model.franchises.gateways.FranchiseRepository;
import co.com.pragma.usecase.common.UseCaseSupport;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RequiredArgsConstructor
public class FindTopStockProductsUseCase {
    private final FranchiseRepository franchiseRepository;
    private final TopStockQueryRepository topStockQueryRepository;

    public Flux<BranchTopStock> execute(UUID franchiseId) {
        return UseCaseSupport.requireResource(
                        () -> franchiseRepository.findById(franchiseId), "Franchise", franchiseId)
                .thenMany(Flux.defer(
                        () -> topStockQueryRepository.findTopActiveProductPerBranchOrdered(franchiseId)));
    }
}
