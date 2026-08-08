package co.com.pragma.api;

import co.com.pragma.api.error.InvalidRequestException;
import co.com.pragma.api.error.MissingPreconditionException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

public final class RequestValues {

    private RequestValues() {
    }

    public static Mono<Long> ifMatch(ServerRequest request) {
        return Mono.just(request.headers().header(HttpHeaders.IF_MATCH))
                .filter(values -> !values.isEmpty())
                .switchIfEmpty(Mono.error(new MissingPreconditionException("If-Match is required")))
                .filter(values -> values.size() == 1)
                .switchIfEmpty(Mono.error(new InvalidRequestException("If-Match must contain one value")))
                .map(List::getFirst)
                .flatMap(value -> Mono.fromCallable(() -> parseVersion(value)))
                .onErrorMap(
                        error -> error instanceof NumberFormatException,
                        error -> new InvalidRequestException("If-Match must contain one strong numeric ETag"));
    }

    private static long parseVersion(String value) {
        return Optional.of(value)
                .filter(candidate -> candidate.matches("\"(0|[1-9][0-9]*)\""))
                .map(candidate -> candidate.substring(1, candidate.length() - 1))
                .map(Long::parseLong)
                .orElseThrow(NumberFormatException::new);
    }
}
