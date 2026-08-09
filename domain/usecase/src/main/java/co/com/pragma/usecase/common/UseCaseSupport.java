package co.com.pragma.usecase.common;

import co.com.pragma.model.common.exception.ResourceNotFoundException;
import co.com.pragma.model.common.exception.VersionConflictException;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Supplier;

public final class UseCaseSupport {

    private UseCaseSupport() {
    }

    public static <T> Mono<T> requireResource(Supplier<Mono<T>> source, String resource, UUID id) {
        return Mono.defer(source)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(resource, id)));
    }

    public static <T> Mono<T> requireVersion(
            T resource, long actualVersion, long expectedVersion, String resourceName, UUID id) {
        return Mono.just(resource)
                .filter(ignored -> actualVersion == expectedVersion)
                .switchIfEmpty(Mono.error(
                        new VersionConflictException(resourceName, id, expectedVersion, actualVersion)));
    }

}
