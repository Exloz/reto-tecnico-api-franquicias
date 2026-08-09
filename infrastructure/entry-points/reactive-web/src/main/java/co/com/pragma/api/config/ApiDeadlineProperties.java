package co.com.pragma.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "api.deadline")
public record ApiDeadlineProperties(Duration timeout) {

    public ApiDeadlineProperties {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("API deadline must be positive");
        }
    }
}
