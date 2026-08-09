package co.com.pragma.r2dbc.resilience;

import co.com.pragma.model.common.exception.DuplicateNameException;
import co.com.pragma.model.common.exception.ServiceUnavailableException;
import co.com.pragma.r2dbc.config.R2dbcResilienceProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.r2dbc.spi.R2dbcException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class R2dbcResilienceTest {

    @Test
    void retriesTransientReadsUntilTheySucceed() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        R2dbcResilience resilience = resilience(meterRegistry, 3);
        AtomicInteger attempts = new AtomicInteger();
        R2dbcException serialization = sqlState("40001");

        StepVerifier.create(resilience.read(() -> Mono.defer(() -> attempts.incrementAndGet() < 3
                        ? Mono.error(serialization)
                        : Mono.just("ok"))))
                .expectNext("ok")
                .verifyComplete();

        assertEquals(3, attempts.get());
        assertEquals(2, resilience.readCircuitBreaker().getMetrics().getNumberOfFailedCalls());
        assertEquals(1, resilience.readCircuitBreaker().getMetrics().getNumberOfSuccessfulCalls());
        assertEquals(2, meterRegistry.get("resilience.r2dbc.retries")
                .tag("operation", "read")
                .tag("reason", "serialization")
                .counter()
                .count());
    }

    @Test
    void operationTimeoutCutsTheCompleteRetryBudget() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        R2dbcResilienceProperties.CircuitBreaker circuitBreaker =
                new R2dbcResilienceProperties.CircuitBreaker(
                        50, 50, Duration.ofMillis(5), 10, 10,
                        Duration.ofMillis(100), 1);
        R2dbcResilienceProperties properties = new R2dbcResilienceProperties(
                new R2dbcResilienceProperties.Policy(
                        Duration.ofMillis(20), Duration.ofMillis(50), circuitBreaker),
                new R2dbcResilienceProperties.Policy(
                        Duration.ofMillis(20), Duration.ofMillis(50), circuitBreaker),
                new R2dbcResilienceProperties.Retry(
                        3, Duration.ofMillis(100), Duration.ofMillis(100), 0));
        R2dbcResilience resilience = new R2dbcResilience(
                properties, new R2dbcFailureClassifier(), meterRegistry);
        AtomicInteger attempts = new AtomicInteger();
        R2dbcException serialization = sqlState("40001");

        StepVerifier.create(resilience.read(() -> Mono.defer(() -> {
                    attempts.incrementAndGet();
                    return Mono.error(serialization);
                })))
                .expectError(ServiceUnavailableException.class)
                .verify();

        assertEquals(1, attempts.get());
    }

    @Test
    void mapsExhaustedRetriesToServiceUnavailable() {
        R2dbcResilience resilience = resilience(new SimpleMeterRegistry(), 3);
        AtomicInteger attempts = new AtomicInteger();
        R2dbcException serialization = sqlState("40001");

        StepVerifier.create(resilience.read(() -> Mono.defer(() -> {
                    attempts.incrementAndGet();
                    return Mono.error(serialization);
                })))
                .expectError(ServiceUnavailableException.class)
                .verify();

        assertEquals(3, attempts.get());
    }

    @Test
    void doesNotRetryAmbiguousWriteTimeouts() {
        R2dbcResilience resilience = resilience(new SimpleMeterRegistry(), 3);
        AtomicInteger attempts = new AtomicInteger();

        StepVerifier.create(resilience.write(() -> Mono.defer(() -> {
                    attempts.incrementAndGet();
                    return Mono.error(new TimeoutException());
                })))
                .expectError(ServiceUnavailableException.class)
                .verify();

        assertEquals(1, attempts.get());
    }

    @Test
    void failsFastWithoutSubscribingWhenCircuitIsOpen() {
        R2dbcResilience resilience = resilience(new SimpleMeterRegistry(), 3);
        AtomicInteger subscriptions = new AtomicInteger();
        resilience.readCircuitBreaker().transitionToOpenState();

        StepVerifier.create(resilience.read(() -> Mono.defer(() -> {
                    subscriptions.incrementAndGet();
                    return Mono.just("unexpected");
                })))
                .expectError(ServiceUnavailableException.class)
                .verify();

        assertEquals(0, subscriptions.get());
    }

    @Test
    void closesCircuitAfterSuccessfulHalfOpenProbe() {
        R2dbcResilience resilience = resilience(new SimpleMeterRegistry(), 1);
        resilience.readCircuitBreaker().transitionToOpenState();
        resilience.readCircuitBreaker().transitionToHalfOpenState();

        StepVerifier.create(resilience.read(() -> Mono.just("recovered")))
                .expectNext("recovered")
                .verifyComplete();

        assertEquals(
                io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED,
                resilience.readCircuitBreaker().getState());
    }

    @Test
    void preservesDomainErrorsWithoutRetryingOrOpeningCircuit() {
        R2dbcResilience resilience = resilience(new SimpleMeterRegistry(), 3);
        AtomicInteger attempts = new AtomicInteger();
        DuplicateNameException duplicate = new DuplicateNameException("Franchise", "Acme");

        StepVerifier.create(resilience.write(() -> Mono.defer(() -> {
                    attempts.incrementAndGet();
                    return Mono.error(duplicate);
                })))
                .expectErrorSatisfies(error -> assertEquals(duplicate, error))
                .verify();

        assertEquals(1, attempts.get());
        assertEquals(0, resilience.writeCircuitBreaker().getMetrics().getNumberOfFailedCalls());
    }

    @Test
    void preservesBackpressureForBoundedDatabaseFlux() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        R2dbcResilience resilience = resilience(meterRegistry, 1);

        StepVerifier.create(resilience.readMany(() -> Flux.range(1, 5)), 0)
                .thenRequest(2)
                .expectNext(1, 2)
                .thenRequest(3)
                .expectNext(3, 4, 5)
                .verifyComplete();

        assertNotNull(meterRegistry.find("resilience4j.circuitbreaker.state")
                .tag("name", "r2dbc-read")
                .gauge());
    }

    @Test
    void doesNotRetryFluxAfterTheFirstElementWasEmitted() {
        R2dbcResilience resilience = resilience(new SimpleMeterRegistry(), 3);
        AtomicInteger attempts = new AtomicInteger();

        StepVerifier.create(resilience.readMany(() -> Flux.defer(() -> {
                    attempts.incrementAndGet();
                    return Flux.concat(Flux.just(1), Flux.error(new TimeoutException()));
                })))
                .expectNext(1)
                .expectError(ServiceUnavailableException.class)
                .verify();

        assertEquals(1, attempts.get());
    }

    @Test
    void retriesAbortedWritesByResubscribingToTheCompleteOperation() {
        R2dbcResilience resilience = resilience(new SimpleMeterRegistry(), 3);
        AtomicInteger transactions = new AtomicInteger();
        R2dbcException deadlock = sqlState("40P01");

        StepVerifier.create(resilience.write(() -> Mono.defer(() -> transactions.incrementAndGet() < 3
                        ? Mono.error(deadlock)
                        : Mono.just("committed"))))
                .expectNext("committed")
                .verifyComplete();

        assertEquals(3, transactions.get());
    }

    private R2dbcResilience resilience(SimpleMeterRegistry meterRegistry, int maxAttempts) {
        R2dbcResilienceProperties.CircuitBreaker circuitBreaker =
                new R2dbcResilienceProperties.CircuitBreaker(
                        50, 50, Duration.ofMillis(50), 10, 10,
                        Duration.ofMillis(100), 1);
        R2dbcResilienceProperties properties = new R2dbcResilienceProperties(
                new R2dbcResilienceProperties.Policy(
                        Duration.ofMillis(100), Duration.ofSeconds(1), circuitBreaker),
                new R2dbcResilienceProperties.Policy(
                        Duration.ofMillis(100), Duration.ofSeconds(1), circuitBreaker),
                new R2dbcResilienceProperties.Retry(
                        maxAttempts, Duration.ofMillis(1), Duration.ofMillis(2), 0));
        return new R2dbcResilience(properties, new R2dbcFailureClassifier(), meterRegistry);
    }

    private R2dbcException sqlState(String sqlState) {
        R2dbcException error = mock(R2dbcException.class);
        when(error.getSqlState()).thenReturn(sqlState);
        return error;
    }
}
