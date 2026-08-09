package co.com.pragma.config;

import co.com.pragma.api.config.ApiDeadlineProperties;
import co.com.pragma.r2dbc.config.R2dbcResilienceProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResilienceBudgetValidatorTest {

    @Test
    void acceptsCoordinatedBudgets() {
        assertDoesNotThrow(() -> new ResilienceBudgetValidator(
                new ApiDeadlineProperties(Duration.ofSeconds(25)),
                properties(Duration.ofSeconds(10), Duration.ofSeconds(15))));
    }

    @Test
    void rejectsDatabaseBudgetThatReachesApiDeadline() {
        assertThrows(IllegalArgumentException.class, () -> new ResilienceBudgetValidator(
                new ApiDeadlineProperties(Duration.ofSeconds(15)),
                properties(Duration.ofSeconds(10), Duration.ofSeconds(15))));
    }

    @Test
    void rejectsApiDeadlineThatReachesGatewayTimeout() {
        assertThrows(IllegalArgumentException.class, () -> new ResilienceBudgetValidator(
                new ApiDeadlineProperties(Duration.ofSeconds(30)),
                properties(Duration.ofSeconds(10), Duration.ofSeconds(15))));
    }

    private R2dbcResilienceProperties properties(Duration readTimeout, Duration writeTimeout) {
        R2dbcResilienceProperties.CircuitBreaker circuitBreaker =
                new R2dbcResilienceProperties.CircuitBreaker(
                        50, 50, Duration.ofSeconds(1), 20, 10,
                        Duration.ofSeconds(30), 3);
        return new R2dbcResilienceProperties(
                new R2dbcResilienceProperties.Policy(
                        Duration.ofSeconds(2), readTimeout, circuitBreaker),
                new R2dbcResilienceProperties.Policy(
                        Duration.ofSeconds(3), writeTimeout, circuitBreaker),
                new R2dbcResilienceProperties.Retry(
                        3, Duration.ofMillis(250), Duration.ofSeconds(2), 0.5));
    }
}
