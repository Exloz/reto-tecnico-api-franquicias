package co.com.pragma.usecase.createfranchise;

import co.com.pragma.model.common.DomainRules;
import co.com.pragma.model.franchises.Franchise;
import co.com.pragma.model.franchises.gateways.FranchiseRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class CreateFranchiseUseCase {
    private static final int NAME_MAX_LENGTH = 120;

    private final FranchiseRepository franchiseRepository;

    public Mono<Franchise> execute(String name) {
        return Mono.fromCallable(() -> DomainRules.normalizeName(name, NAME_MAX_LENGTH))
                .flatMap(normalizedName -> Mono.defer(() -> {
                    Instant now = Instant.now();
                    return franchiseRepository.create(Franchise.builder()
                            .id(UUID.randomUUID())
                            .name(normalizedName.value())
                            .normalizedName(normalizedName.normalized())
                            .version(0)
                            .createdAt(now)
                            .updatedAt(now)
                            .build());
                }));
    }
}
