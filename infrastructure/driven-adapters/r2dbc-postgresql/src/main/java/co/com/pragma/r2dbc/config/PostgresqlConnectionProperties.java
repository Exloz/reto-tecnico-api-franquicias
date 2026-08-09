package co.com.pragma.r2dbc.config;

import io.r2dbc.postgresql.client.SSLMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "adapters.r2dbc")
public record PostgresqlConnectionProperties(
        String host,
        Integer port,
        String database,
        String schema,
        String username,
        String password,
        SSLMode sslMode,
        String sslRootCert,
        Integer initialSize,
        Integer maxSize,
        Duration maxIdleTime,
        Duration maxAcquireTime,
        Duration maxLifeTime,
        Duration connectTimeout) {

    public PostgresqlConnectionProperties {
        if (initialSize == null || initialSize < 0 || maxSize == null || maxSize < 1
                || initialSize > maxSize) {
            throw new IllegalArgumentException("R2DBC pool sizes are invalid");
        }
        requirePositive(maxIdleTime, "maxIdleTime");
        requirePositive(maxAcquireTime, "maxAcquireTime");
        requirePositive(maxLifeTime, "maxLifeTime");
        requirePositive(connectTimeout, "connectTimeout");
        if (connectTimeout.compareTo(maxAcquireTime) >= 0) {
            throw new IllegalArgumentException("connectTimeout must be shorter than maxAcquireTime");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
