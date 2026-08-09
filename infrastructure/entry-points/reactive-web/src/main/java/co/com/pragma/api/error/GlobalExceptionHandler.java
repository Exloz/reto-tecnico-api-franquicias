package co.com.pragma.api.error;

import co.com.pragma.api.filter.CorrelationIdFilter;
import co.com.pragma.api.filter.RequestObservabilityFilter;
import co.com.pragma.model.common.exception.DuplicateNameException;
import co.com.pragma.model.common.exception.InvalidNameException;
import co.com.pragma.model.common.exception.InvalidPageSizeException;
import co.com.pragma.model.common.exception.InvalidStockException;
import co.com.pragma.model.common.exception.InvalidVersionException;
import co.com.pragma.model.common.exception.ResourceNotFoundException;
import co.com.pragma.model.common.exception.VersionConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Component
@Order(-2)
public class GlobalExceptionHandler implements WebExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");
    private static final String PROBLEM_PREFIX = "urn:franchise-api:problem:";
    private static final ErrorDescriptor INTERNAL = new ErrorDescriptor(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal-error",
            "Internal server error",
            "An unexpected error occurred",
            false);
    private static final List<ErrorMapping> MAPPINGS = List.of(
            mapping(InvalidRequestException.class, HttpStatus.BAD_REQUEST,
                    "invalid-request", "Invalid request", true),
            mapping(InvalidPageSizeException.class, HttpStatus.BAD_REQUEST,
                    "invalid-page-size", "Invalid page size", true),
            mapping(InvalidVersionException.class, HttpStatus.BAD_REQUEST,
                    "invalid-version", "Invalid version", true),
            mapping(ResourceNotFoundException.class, HttpStatus.NOT_FOUND,
                    "resource-not-found", "Resource not found", true),
            mapping(NoResourceFoundException.class, HttpStatus.NOT_FOUND,
                    "http-404", "Not Found", false),
            mapping(DuplicateNameException.class, HttpStatus.CONFLICT,
                    "duplicate-name", "Duplicate name", false),
            mapping(VersionConflictException.class, HttpStatus.PRECONDITION_FAILED,
                    "version-conflict", "Version precondition failed", false),
            mapping(InvalidNameException.class, HttpStatus.UNPROCESSABLE_CONTENT,
                    "invalid-name", "Invalid name", true),
            mapping(InvalidStockException.class, HttpStatus.UNPROCESSABLE_CONTENT,
                    "invalid-stock", "Invalid stock", true),
            mapping(MissingPreconditionException.class, HttpStatus.PRECONDITION_REQUIRED,
                    "precondition-required", "Precondition required", true));

    private final ServerCodecConfigurer codecConfigurer;

    public GlobalExceptionHandler(ServerCodecConfigurer codecConfigurer) {
        this.codecConfigurer = codecConfigurer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        return Mono.just(exchange.getResponse())
                .filter(response -> !response.isCommitted())
                .switchIfEmpty(Mono.error(error))
                .flatMap(response -> describe(error)
                        .flatMap(descriptor -> write(exchange, error, descriptor)));
    }

    private Mono<Void> write(ServerWebExchange exchange, Throwable error, ErrorDescriptor descriptor) {
        exchange.getAttributes().put(RequestObservabilityFilter.ERROR_CODE_ATTRIBUTE, descriptor.code());
        String detail = Optional.of(descriptor)
                .filter(ErrorDescriptor::exposeDetail)
                .map(ignored -> error.getMessage())
                .filter(message -> !message.isBlank())
                .orElse(descriptor.detail());
        ProblemResponse problem = new ProblemResponse(
                URI.create(PROBLEM_PREFIX + descriptor.code()),
                descriptor.title(),
                descriptor.status().value(),
                detail,
                exchange.getRequest().getURI(),
                Optional.ofNullable(exchange.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME))
                        .map(Object::toString)
                        .orElseGet(CorrelationIdFilter::newCorrelationId));
        return Mono.just(descriptor)
                .filter(INTERNAL::equals)
                .doOnNext(ignored -> LOGGER.atError()
                        .addKeyValue("event", "request.failure")
                        .addKeyValue("correlationId", Optional.ofNullable(
                                        exchange.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME))
                                .map(Object::toString)
                                .orElse("unknown"))
                        .addKeyValue("apiGatewayRequestId", Optional.ofNullable(exchange.getRequest().getHeaders()
                                        .getFirst(RequestObservabilityFilter.API_GATEWAY_REQUEST_ID_HEADER))
                                .filter(value -> value.matches("[\\x20-\\x7E]{1,128}"))
                                .orElse("unknown"))
                        .addKeyValue("errorCode", descriptor.code())
                        .addKeyValue("errorType", error.getClass().getName())
                        .log("Unhandled request failure"))
                .then(ServerResponse.status(descriptor.status())
                        .contentType(PROBLEM_JSON)
                        .bodyValue(problem)
                        .flatMap(response -> response.writeTo(exchange, responseContext())));
    }

    private Mono<ErrorDescriptor> describe(Throwable error) {
        return Mono.just(error)
                .ofType(ResponseStatusException.class)
                .map(this::fromResponseStatus)
                .switchIfEmpty(Flux.fromIterable(MAPPINGS)
                        .filter(mapping -> mapping.errorType().isInstance(error))
                        .next()
                        .map(ErrorMapping::descriptor)
                        .defaultIfEmpty(INTERNAL));
    }

    private ErrorDescriptor fromResponseStatus(ResponseStatusException error) {
        HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
        return new ErrorDescriptor(
                status,
                "http-" + status.value(),
                status.getReasonPhrase(),
                Optional.ofNullable(error.getReason()).orElse(status.getReasonPhrase()),
                false);
    }

    private ServerResponse.Context responseContext() {
        return new ServerResponse.Context() {
            @Override
            public List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters() {
                return codecConfigurer.getWriters();
            }

            @Override
            public List<ViewResolver> viewResolvers() {
                return List.of();
            }
        };
    }

    private static ErrorMapping mapping(
            Class<? extends Throwable> errorType,
            HttpStatus status,
            String code,
            String title,
            boolean exposeDetail) {
        return new ErrorMapping(
                errorType,
                new ErrorDescriptor(status, code, title, status.getReasonPhrase(), exposeDetail));
    }

    private record ErrorMapping(Class<? extends Throwable> errorType, ErrorDescriptor descriptor) {
    }

    private record ErrorDescriptor(
            HttpStatus status,
            String code,
            String title,
            String detail,
            boolean exposeDetail) {
    }
}
