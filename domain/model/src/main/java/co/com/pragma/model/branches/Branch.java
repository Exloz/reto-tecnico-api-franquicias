package co.com.pragma.model.branches;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Branch {
    private final UUID id;
    private final UUID franchiseId;
    private final String name;
    private final String normalizedName;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;
}
