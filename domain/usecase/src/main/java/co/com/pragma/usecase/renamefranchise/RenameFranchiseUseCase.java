package co.com.pragma.usecase.renamefranchise;

import co.com.pragma.model.common.DomainRules;
import co.com.pragma.model.franchises.Franchise;
import co.com.pragma.model.franchises.gateways.FranchiseRepository;
import co.com.pragma.usecase.common.UseCaseSupport;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class RenameFranchiseUseCase {
    private static final int NAME_MAX_LENGTH = 120;

    private final FranchiseRepository franchiseRepository;

    public Mono<Franchise> execute(UUID franchiseId, String name, long expectedVersion) {
        return Mono.fromCallable(() -> DomainRules.normalizeName(name, NAME_MAX_LENGTH))
                .zipWith(Mono.fromCallable(() -> DomainRules.validateExpectedVersion(expectedVersion)))
                .flatMap(input -> UseCaseSupport.requireResource(
                                () -> franchiseRepository.findById(franchiseId), "Franchise", franchiseId)
                        .flatMap(franchise -> UseCaseSupport.requireVersion(
                                franchise,
                                franchise.getVersion(),
                                input.getT2(),
                                "Franchise",
                                franchiseId))
                        .flatMap(franchise -> Mono.just(franchise)
                                .filter(current -> current.getNormalizedName().equals(input.getT1().normalized()))
                                .switchIfEmpty(Mono.defer(() -> franchiseRepository.rename(franchise.toBuilder()
                                                .name(input.getT1().value())
                                                .normalizedName(input.getT1().normalized())
                                                .version(franchise.getVersion() + 1)
                                                .updatedAt(Instant.now())
                                                .build(), input.getT2())))));
    }
}
