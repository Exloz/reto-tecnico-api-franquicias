package co.com.pragma.r2dbc;

import co.com.pragma.model.branches.Branch;
import co.com.pragma.model.branches.gateways.BranchRepository;
import co.com.pragma.model.common.exception.DuplicateNameException;
import co.com.pragma.model.common.exception.ResourceNotFoundException;
import co.com.pragma.model.common.exception.VersionConflictException;
import co.com.pragma.r2dbc.entity.BranchData;
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
public class BranchR2dbcAdapter implements BranchRepository {
    private static final String COLUMNS = """
            id, franchise_id, name, normalized_name, version, created_at, updated_at
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;
    private final R2dbcResilience resilience;

    @Override
    public Mono<Branch> findByIdAndFranchiseId(UUID branchId, UUID franchiseId) {
        return resilience.read(() -> findByIdAndFranchiseIdRaw(branchId, franchiseId));
    }

    private Mono<Branch> findByIdAndFranchiseIdRaw(UUID branchId, UUID franchiseId) {
        return databaseClient.sql("SELECT " + COLUMNS + " FROM franchise.branches "
                        + "WHERE id = :id AND franchise_id = :franchiseId")
                .bind("id", branchId)
                .bind("franchiseId", franchiseId)
                .map((row, metadata) -> BranchData.from(row).toDomain())
                .one();
    }

    @Override
    public Mono<Branch> create(Branch branch) {
        BranchData data = BranchData.from(branch);
        return resilience.write(() -> databaseClient.sql("""
                        INSERT INTO franchise.branches
                            (id, franchise_id, name, normalized_name, version, created_at, updated_at)
                        VALUES (:id, :franchiseId, :name, :normalizedName, :version, :createdAt, :updatedAt)
                        RETURNING
                        """ + COLUMNS)
                .bind("id", data.id())
                .bind("franchiseId", data.franchiseId())
                .bind("name", data.name())
                .bind("normalizedName", data.normalizedName())
                .bind("version", data.version())
                .bind("createdAt", OffsetDateTime.ofInstant(data.createdAt(), ZoneOffset.UTC))
                .bind("updatedAt", OffsetDateTime.ofInstant(data.updatedAt(), ZoneOffset.UTC))
                .map((row, metadata) -> BranchData.from(row).toDomain())
                .one()
                .onErrorMap(
                        error -> PostgresqlErrorMapper.hasConstraint(
                                error, "uq_branches_franchise_normalized_name"),
                        error -> new DuplicateNameException("Branch", data.name()))
                .onErrorMap(
                        error -> PostgresqlErrorMapper.hasSqlState(error, "23503"),
                        error -> new ResourceNotFoundException("Franchise", data.franchiseId())));
    }

    @Override
    public Mono<Branch> rename(Branch branch, long expectedVersion) {
        BranchData data = BranchData.from(branch);
        return resilience.write(() -> databaseClient.sql("""
                        UPDATE franchise.branches
                        SET name = :name,
                            normalized_name = :normalizedName,
                            version = version + 1,
                            updated_at = :updatedAt
                        WHERE id = :id
                          AND franchise_id = :franchiseId
                          AND version = :expectedVersion
                        RETURNING
                        """ + COLUMNS)
                .bind("name", data.name())
                .bind("normalizedName", data.normalizedName())
                .bind("updatedAt", OffsetDateTime.ofInstant(data.updatedAt(), ZoneOffset.UTC))
                .bind("id", data.id())
                .bind("franchiseId", data.franchiseId())
                .bind("expectedVersion", expectedVersion)
                .map((row, metadata) -> BranchData.from(row).toDomain())
                .one()
                .switchIfEmpty(Mono.defer(() -> diagnoseUpdate(data, expectedVersion)))
                .onErrorMap(
                        error -> PostgresqlErrorMapper.hasConstraint(
                                error, "uq_branches_franchise_normalized_name"),
                        error -> new DuplicateNameException("Branch", data.name()))
                .as(transactionalOperator::transactional));
    }

    private Mono<Branch> diagnoseUpdate(BranchData data, long expectedVersion) {
        return findByIdAndFranchiseIdRaw(data.id(), data.franchiseId())
                .flatMap(current -> Mono.<Branch>error(new VersionConflictException(
                        "Branch", data.id(), expectedVersion, current.getVersion())))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Branch", data.id())));
    }
}
