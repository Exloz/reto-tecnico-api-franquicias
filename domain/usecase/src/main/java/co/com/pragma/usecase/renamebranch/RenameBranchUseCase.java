package co.com.pragma.usecase.renamebranch;

import co.com.pragma.model.branches.Branch;
import co.com.pragma.model.branches.gateways.BranchRepository;
import co.com.pragma.model.common.DomainRules;
import co.com.pragma.usecase.common.UseCaseSupport;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class RenameBranchUseCase {
    private static final int NAME_MAX_LENGTH = 120;

    private final BranchRepository branchRepository;

    public Mono<Branch> execute(UUID franchiseId, UUID branchId, String name, long expectedVersion) {
        return Mono.fromCallable(() -> DomainRules.normalizeName(name, NAME_MAX_LENGTH))
                .zipWith(Mono.fromCallable(() -> DomainRules.validateExpectedVersion(expectedVersion)))
                .flatMap(input -> UseCaseSupport.requireResource(
                                () -> branchRepository.findByIdAndFranchiseId(branchId, franchiseId),
                                "Branch",
                                branchId)
                        .flatMap(branch -> UseCaseSupport.requireVersion(
                                branch, branch.getVersion(), input.getT2(), "Branch", branchId))
                        .flatMap(branch -> Mono.just(branch)
                                .filter(current -> current.getNormalizedName().equals(input.getT1().normalized()))
                                .switchIfEmpty(Mono.defer(() -> branchRepository.rename(branch.toBuilder()
                                                .name(input.getT1().value())
                                                .normalizedName(input.getT1().normalized())
                                                .version(branch.getVersion() + 1)
                                                .updatedAt(Instant.now())
                                                .build(), input.getT2())))));
    }
}
