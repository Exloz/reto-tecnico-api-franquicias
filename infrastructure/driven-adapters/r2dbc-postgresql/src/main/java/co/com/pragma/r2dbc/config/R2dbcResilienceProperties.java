package co.com.pragma.r2dbc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "adapters.r2dbc.resilience")
public record R2dbcResilienceProperties(
        Policy read,
        Policy write,
        Retry retry) {

    public R2dbcResilienceProperties {
        if (read == null || write == null || retry == null) {
            throw new IllegalArgumentException("R2DBC resilience policies are required");
        }
    }

    public record Policy(
            Duration attemptTimeout,
            Duration operationTimeout,
            CircuitBreaker circuitBreaker) {

        public Policy {
            requirePositive(attemptTimeout, "attemptTimeout");
            requirePositive(operationTimeout, "operationTimeout");
            if (attemptTimeout.compareTo(operationTimeout) >= 0) {
                throw new IllegalArgumentException("attemptTimeout must be shorter than operationTimeout");
            }
            if (circuitBreaker == null) {
                throw new IllegalArgumentException("circuitBreaker is required");
            }
            if (circuitBreaker.slowCallDurationThreshold().compareTo(attemptTimeout) >= 0) {
                throw new IllegalArgumentException("slowCallDurationThreshold must be shorter than attemptTimeout");
            }
        }
    }

    public record CircuitBreaker(
            float failureRateThreshold,
            float slowCallRateThreshold,
            Duration slowCallDurationThreshold,
            int slidingWindowSize,
            int minimumNumberOfCalls,
            Duration waitDurationInOpenState,
            int permittedNumberOfCallsInHalfOpenState) {

        public CircuitBreaker {
            requirePercentage(failureRateThreshold, "failureRateThreshold");
            requirePercentage(slowCallRateThreshold, "slowCallRateThreshold");
            requirePositive(slowCallDurationThreshold, "slowCallDurationThreshold");
            requirePositive(waitDurationInOpenState, "waitDurationInOpenState");
            if (slidingWindowSize < 1 || minimumNumberOfCalls < 1
                    || minimumNumberOfCalls > slidingWindowSize) {
                throw new IllegalArgumentException("Circuit breaker window is invalid");
            }
            if (permittedNumberOfCallsInHalfOpenState < 1) {
                throw new IllegalArgumentException("Half-open calls must be positive");
            }
        }
    }

    public record Retry(
            int maxAttempts,
            Duration minBackoff,
            Duration maxBackoff,
            double jitter) {

        public Retry {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be positive");
            }
            requirePositive(minBackoff, "minBackoff");
            requirePositive(maxBackoff, "maxBackoff");
            if (minBackoff.compareTo(maxBackoff) > 0) {
                throw new IllegalArgumentException("minBackoff must not exceed maxBackoff");
            }
            if (jitter < 0 || jitter > 1) {
                throw new IllegalArgumentException("jitter must be between 0 and 1");
            }
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requirePercentage(float value, String name) {
        if (value < 1 || value > 100) {
            throw new IllegalArgumentException(name + " must be between 1 and 100");
        }
    }
}
