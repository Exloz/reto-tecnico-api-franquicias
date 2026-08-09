package co.com.pragma.usecase.findtopstockproducts;

import co.com.pragma.model.branchproducts.BranchTopStock;
import co.com.pragma.model.branchproducts.TopStockCursor;
import co.com.pragma.model.branchproducts.TopStockPage;
import co.com.pragma.model.branchproducts.gateways.TopStockQueryRepository;
import co.com.pragma.model.common.exception.InvalidPageSizeException;
import co.com.pragma.model.franchises.gateways.FranchiseRepository;
import co.com.pragma.usecase.common.UseCaseSupport;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class FindTopStockProductsUseCase {
    private static final int MAXIMUM_LIMIT = 100;

    private final FranchiseRepository franchiseRepository;
    private final TopStockQueryRepository topStockQueryRepository;

    public Mono<TopStockPage> execute(UUID franchiseId, TopStockCursor cursor, int limit) {
        return Mono.fromCallable(() -> validateLimit(limit))
                .flatMap(validLimit -> UseCaseSupport.requireResource(
                        () -> franchiseRepository.findById(franchiseId), "Franchise", franchiseId)
                .thenMany(Flux.defer(() -> topStockQueryRepository.findTopActiveProductPerBranchOrdered(
                        franchiseId, cursor, validLimit + 1)))
                .take(validLimit + 1L)
                .collectList()
                .map(rows -> toPage(rows, validLimit)));
    }

    private int validateLimit(int limit) {
        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new InvalidPageSizeException(limit, MAXIMUM_LIMIT);
        }
        return limit;
    }

    private TopStockPage toPage(List<BranchTopStock> rows, int limit) {
        List<BranchTopStock> items = List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
        TopStockCursor nextCursor = Optional.of(rows)
                .filter(results -> results.size() > limit)
                .map(results -> items.get(items.size() - 1))
                .map(item -> new TopStockCursor(item.branchNormalizedName(), item.branchId()))
                .orElse(null);
        return new TopStockPage(items, nextCursor);
    }
}
