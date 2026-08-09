package co.com.pragma.config;

import co.com.pragma.api.config.ApiDeadlineProperties;
import co.com.pragma.r2dbc.config.R2dbcResilienceProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ResilienceBudgetValidator {
    private static final Duration API_GATEWAY_TIMEOUT = Duration.ofSeconds(30);

    public ResilienceBudgetValidator(
            ApiDeadlineProperties api,
            R2dbcResilienceProperties r2dbc) {
        requireShorter(r2dbc.read().operationTimeout(), api.timeout(), "read operation", "API deadline");
        requireShorter(r2dbc.write().operationTimeout(), api.timeout(), "write operation", "API deadline");
        requireShorter(api.timeout(), API_GATEWAY_TIMEOUT, "API deadline", "API Gateway timeout");
    }

    private void requireShorter(Duration lower, Duration upper, String lowerName, String upperName) {
        if (lower.compareTo(upper) >= 0) {
            throw new IllegalArgumentException(lowerName + " must be shorter than " + upperName);
        }
    }
}
