package co.com.pragma.model.franchises.gateways;

import co.com.pragma.model.franchises.Franchise;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FranchiseRepository {
    Mono<Franchise> findById(UUID franchiseId);

    Mono<Franchise> create(Franchise franchise);

    Mono<Franchise> rename(Franchise franchise, long expectedVersion);
}
