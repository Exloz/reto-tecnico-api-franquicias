package co.com.pragma.r2dbc.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class R2dbcResiliencePropertiesTest {

    @Test
    void rejectsAttemptTimeoutThatConsumesEntireOperationBudget() {
        R2dbcResilienceProperties.CircuitBreaker circuitBreaker = circuitBreaker();

        assertThrows(IllegalArgumentException.class, () -> new R2dbcResilienceProperties.Policy(
                Duration.ofSeconds(5), Duration.ofSeconds(5), circuitBreaker));
    }

    @Test
    void rejectsIncoherentCircuitBreakerWindow() {
        assertThrows(IllegalArgumentException.class, () ->
                new R2dbcResilienceProperties.CircuitBreaker(
                        50, 50, Duration.ofSeconds(2), 5, 10,
                        Duration.ofSeconds(30), 3));
    }

    @Test
    void rejectsBackoffOutsideOperationPolicy() {
        assertThrows(IllegalArgumentException.class, () -> new R2dbcResilienceProperties.Retry(
                3, Duration.ofSeconds(2), Duration.ofSeconds(1), 0.5));
    }

    private R2dbcResilienceProperties.CircuitBreaker circuitBreaker() {
        return new R2dbcResilienceProperties.CircuitBreaker(
                50, 50, Duration.ofSeconds(2), 20, 10,
                Duration.ofSeconds(30), 3);
    }
}
