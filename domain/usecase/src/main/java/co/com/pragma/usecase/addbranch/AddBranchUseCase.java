package co.com.pragma.usecase.addbranch;

import co.com.pragma.model.branches.Branch;
import co.com.pragma.model.branches.gateways.BranchRepository;
import co.com.pragma.model.common.DomainRules;
import co.com.pragma.model.franchises.gateways.FranchiseRepository;
import co.com.pragma.usecase.common.UseCaseSupport;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class AddBranchUseCase {
    private static final int NAME_MAX_LENGTH = 120;

    private final FranchiseRepository franchiseRepository;
    private final BranchRepository branchRepository;

    public Mono<Branch> execute(UUID franchiseId, String name) {
        return Mono.fromCallable(() -> DomainRules.normalizeName(name, NAME_MAX_LENGTH))
                .flatMap(normalizedName -> UseCaseSupport.requireResource(
                                () -> franchiseRepository.findById(franchiseId), "Franchise", franchiseId)
                        .then(Mono.defer(() -> {
                            Instant now = Instant.now();
                            return branchRepository.create(Branch.builder()
                                    .id(UUID.randomUUID())
                                    .franchiseId(franchiseId)
                                    .name(normalizedName.value())
                                    .normalizedName(normalizedName.normalized())
                                    .version(0)
                                    .createdAt(now)
                                    .updatedAt(now)
                                    .build());
                        })));
    }
}
