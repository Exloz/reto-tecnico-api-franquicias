package co.com.pragma.r2dbc;

import co.com.pragma.model.common.exception.DuplicateNameException;
import co.com.pragma.model.common.exception.ResourceNotFoundException;
import co.com.pragma.model.common.exception.VersionConflictException;
import co.com.pragma.model.franchises.Franchise;
import co.com.pragma.model.franchises.gateways.FranchiseRepository;
import co.com.pragma.r2dbc.entity.FranchiseData;
import co.com.pragma.r2dbc.resilience.R2dbcResilience;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FranchiseR2dbcAdapter implements FranchiseRepository {
    private static final String COLUMNS = """
            id, name, normalized_name, version, created_at, updated_at
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;
    private final R2dbcResilience resilience;

    @Override
    public Mono<Franchise> findById(UUID franchiseId) {
        return resilience.read(() -> findByIdRaw(franchiseId));
    }

    private Mono<Franchise> findByIdRaw(UUID franchiseId) {
        return databaseClient.sql("SELECT " + COLUMNS + " FROM franchise.franchises WHERE id = :id")
                .bind("id", franchiseId)
                .map((row, metadata) -> FranchiseData.from(row).toDomain())
                .one();
    }

    @Override
    public Mono<Franchise> create(Franchise franchise) {
        FranchiseData data = FranchiseData.from(franchise);
        return resilience.write(() -> databaseClient.sql("""
                        INSERT INTO franchise.franchises
                            (id, name, normalized_name, version, created_at, updated_at)
                        VALUES (:id, :name, :normalizedName, :version, :createdAt, :updatedAt)
                        RETURNING
                        """ + COLUMNS)
                .bind("id", data.id())
                .bind("name", data.name())
                .bind("normalizedName", data.normalizedName())
                .bind("version", data.version())
                .bind("createdAt", OffsetDateTime.ofInstant(data.createdAt(), ZoneOffset.UTC))
                .bind("updatedAt", OffsetDateTime.ofInstant(data.updatedAt(), ZoneOffset.UTC))
                .map((row, metadata) -> FranchiseData.from(row).toDomain())
                .one()
                .onErrorMap(
                        error -> PostgresqlErrorMapper.hasConstraint(error, "uq_franchises_normalized_name"),
                        error -> new DuplicateNameException("Franchise", data.name())));
    }

    @Override
    public Mono<Franchise> rename(Franchise franchise, long expectedVersion) {
        FranchiseData data = FranchiseData.from(franchise);
        return resilience.write(() -> databaseClient.sql("""
                        UPDATE franchise.franchises
                        SET name = :name,
                            normalized_name = :normalizedName,
                            version = version + 1,
                            updated_at = :updatedAt
                        WHERE id = :id AND version = :expectedVersion
                        RETURNING
                        """ + COLUMNS)
                .bind("name", data.name())
                .bind("normalizedName", data.normalizedName())
                .bind("updatedAt", OffsetDateTime.ofInstant(data.updatedAt(), ZoneOffset.UTC))
                .bind("id", data.id())
                .bind("expectedVersion", expectedVersion)
                .map((row, metadata) -> FranchiseData.from(row).toDomain())
                .one()
                .switchIfEmpty(Mono.defer(() -> diagnoseUpdate(data.id(), expectedVersion)))
                .onErrorMap(
                        error -> PostgresqlErrorMapper.hasConstraint(error, "uq_franchises_normalized_name"),
                        error -> new DuplicateNameException("Franchise", data.name()))
                .as(transactionalOperator::transactional));
    }

    private Mono<Franchise> diagnoseUpdate(UUID id, long expectedVersion) {
        return findByIdRaw(id)
                .flatMap(current -> Mono.<Franchise>error(new VersionConflictException(
                        "Franchise", id, expectedVersion, current.getVersion())))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Franchise", id)));
    }
}
