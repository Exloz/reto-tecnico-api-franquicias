package co.com.pragma.model.branches.gateways;

import co.com.pragma.model.branches.Branch;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface BranchRepository {
    Mono<Branch> findByIdAndFranchiseId(UUID branchId, UUID franchiseId);

    Mono<Branch> create(Branch branch);

    Mono<Branch> rename(Branch branch, long expectedVersion);
}
