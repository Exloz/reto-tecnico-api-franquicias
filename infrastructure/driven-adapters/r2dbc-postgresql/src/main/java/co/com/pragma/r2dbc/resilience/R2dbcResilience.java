package co.com.pragma.r2dbc.resilience;

import co.com.pragma.model.common.exception.ServiceUnavailableException;
import co.com.pragma.r2dbc.config.R2dbcResilienceProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Component
public class R2dbcResilience {
    private static final Logger LOGGER = LoggerFactory.getLogger(R2dbcResilience.class);
    private static final String READ = "read";
    private static final String WRITE = "write";

    private final R2dbcResilienceProperties properties;
    private final R2dbcFailureClassifier classifier;
    private final MeterRegistry meterRegistry;
    private final CircuitBreaker readCircuitBreaker;
    private final CircuitBreaker writeCircuitBreaker;
    private final Retry readRetry;
    private final Retry writeRetry;

    public R2dbcResilience(
            R2dbcResilienceProperties properties,
            R2dbcFailureClassifier classifier,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.classifier = classifier;
        this.meterRegistry = meterRegistry;
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(Map.of(
                READ, circuitBreakerConfig(properties.read()),
                WRITE, circuitBreakerConfig(properties.write())));
        this.readCircuitBreaker = registry.circuitBreaker("r2dbc-read", READ);
        this.writeCircuitBreaker = registry.circuitBreaker("r2dbc-write", WRITE);
        bindTransitionLogs(readCircuitBreaker, READ);
        bindTransitionLogs(writeCircuitBreaker, WRITE);
        this.readRetry = retry(READ, classifier::retryRead);
        this.writeRetry = retry(WRITE, classifier::retryWrite);
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry);
    }

    public <T> Mono<T> read(Supplier<Mono<T>> operation) {
        return protectMono(operation, properties.read(), readCircuitBreaker, readRetry, READ);
    }

    public <T> Mono<T> write(Supplier<Mono<T>> operation) {
        return protectMono(operation, properties.write(), writeCircuitBreaker, writeRetry, WRITE);
    }

    public <T> Flux<T> readMany(Supplier<Flux<T>> operation) {
        return Flux.defer(() -> {
            AtomicBoolean emitted = new AtomicBoolean();
            Retry retryBeforeFirstElement = retry(
                    READ, error -> !emitted.get() && classifier.retryRead(error));
            return Flux.defer(() -> withDeadline(operation.get(), properties.read().attemptTimeout()))
                    .transformDeferred(CircuitBreakerOperator.of(readCircuitBreaker))
                    .doOnNext(ignored -> emitted.set(true))
                    .retryWhen(retryBeforeFirstElement)
                    .transformDeferred(source -> withDeadline(source, properties.read().operationTimeout()))
                    .transformDeferred(source -> record(source, READ))
                    .onErrorMap(classifier::unavailable, error -> unavailable(error, READ));
        });
    }

    CircuitBreaker readCircuitBreaker() {
        return readCircuitBreaker;
    }

    CircuitBreaker writeCircuitBreaker() {
        return writeCircuitBreaker;
    }

    private <T> Mono<T> protectMono(
            Supplier<Mono<T>> operation,
            R2dbcResilienceProperties.Policy policy,
            CircuitBreaker circuitBreaker,
            Retry retry,
            String operationType) {
        return Mono.defer(operation)
                .timeout(policy.attemptTimeout())
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .retryWhen(retry)
                .timeout(policy.operationTimeout())
                .transformDeferred(source -> record(source, operationType))
                .onErrorMap(classifier::unavailable, error -> unavailable(error, operationType));
    }

    private CircuitBreakerConfig circuitBreakerConfig(R2dbcResilienceProperties.Policy policy) {
        R2dbcResilienceProperties.CircuitBreaker circuitBreaker = policy.circuitBreaker();
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(circuitBreaker.failureRateThreshold())
                .slowCallRateThreshold(circuitBreaker.slowCallRateThreshold())
                .slowCallDurationThreshold(circuitBreaker.slowCallDurationThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(circuitBreaker.slidingWindowSize())
                .minimumNumberOfCalls(circuitBreaker.minimumNumberOfCalls())
                .waitDurationInOpenState(circuitBreaker.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(
                        circuitBreaker.permittedNumberOfCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordException(classifier::recordCircuitFailure)
                .ignoreException(classifier::domainFailure)
                .build();
    }

    private Retry retry(String operationType, Predicate<Throwable> retryable) {
        R2dbcResilienceProperties.Retry retry = properties.retry();
        if (retry.maxAttempts() == 1) {
            return Retry.max(0).filter(retryable);
        }
        return Retry.backoff(retry.maxAttempts() - 1L, retry.minBackoff())
                .maxBackoff(retry.maxBackoff())
                .jitter(retry.jitter())
                .filter(retryable)
                .doBeforeRetry(signal -> {
                    String reason = classifier.reason(signal.failure());
                    meterRegistry.counter(
                                    "resilience.r2dbc.retries",
                                    "operation", operationType,
                                    "reason", reason)
                            .increment();
                    LOGGER.atWarn()
                            .addKeyValue("event", "r2dbc.retry")
                            .addKeyValue("operation", operationType)
                            .addKeyValue("reason", reason)
                            .addKeyValue("attempt", signal.totalRetries() + 2)
                            .log("Retrying PostgreSQL operation");
                })
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    private ServiceUnavailableException unavailable(Throwable error, String operationType) {
        String reason = classifier.reason(error);
        meterRegistry.counter(
                        "resilience.r2dbc.unavailable",
                        "operation", operationType,
                        "reason", reason)
                .increment();
        LOGGER.atWarn()
                .addKeyValue("event", "r2dbc.unavailable")
                .addKeyValue("operation", operationType)
                .addKeyValue("reason", reason)
                .addKeyValue("errorType", error.getClass().getName())
                .log("PostgreSQL operation unavailable");
        return new ServiceUnavailableException(error);
    }

    private void bindTransitionLogs(CircuitBreaker circuitBreaker, String operationType) {
        circuitBreaker.getEventPublisher().onStateTransition(event -> LOGGER.atWarn()
                .addKeyValue("event", "r2dbc.circuit.transition")
                .addKeyValue("operation", operationType)
                .addKeyValue("fromState", event.getStateTransition().getFromState().name())
                .addKeyValue("toState", event.getStateTransition().getToState().name())
                .log("PostgreSQL circuit breaker changed state"));
    }

    private <T> Mono<T> record(Mono<T> source, String operationType) {
        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            AtomicReference<String> outcome = new AtomicReference<>("success");
            return source
                    .doOnError(error -> outcome.set(outcome(error)))
                    .doOnCancel(() -> outcome.set("cancelled"))
                    .doFinally(ignored -> sample.stop(operationTimer(operationType, outcome.get())));
        });
    }

    private <T> Flux<T> record(Flux<T> source, String operationType) {
        return Flux.defer(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            AtomicReference<String> outcome = new AtomicReference<>("success");
            return source
                    .doOnError(error -> outcome.set(outcome(error)))
                    .doOnCancel(() -> outcome.set("cancelled"))
                    .doFinally(ignored -> sample.stop(operationTimer(operationType, outcome.get())));
        });
    }

    private Timer operationTimer(String operationType, String outcome) {
        return Timer.builder("resilience.r2dbc.operation")
                .tag("operation", operationType)
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    private String outcome(Throwable error) {
        if (classifier.domainFailure(error)) {
            return "domain_error";
        }
        if (classifier.unavailable(error)) {
            return "unavailable";
        }
        return "error";
    }

    private <T> Flux<T> withDeadline(Flux<T> source, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        return source.timeout(deadline(deadline), ignored -> deadline(deadline));
    }

    private Publisher<Long> deadline(long deadline) {
        long remaining = Math.max(0, deadline - System.nanoTime());
        return Mono.delay(Duration.ofNanos(remaining));
    }
}
