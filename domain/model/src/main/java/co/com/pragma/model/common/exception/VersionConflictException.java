package co.com.pragma.model.common.exception;

import java.util.UUID;

public class VersionConflictException extends RuntimeException {

    public VersionConflictException(String resource, UUID id, long expectedVersion, long actualVersion) {
        super(resource + " version conflict for " + id + ": expected " + expectedVersion
                + " but was " + actualVersion);
    }
}
