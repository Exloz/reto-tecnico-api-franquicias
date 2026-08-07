package co.com.pragma.model.branchproducts;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BranchProduct {
    private final UUID id;
    private final UUID branchId;
    private final String name;
    private final String normalizedName;
    private final int stock;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant deletedAt;
}
