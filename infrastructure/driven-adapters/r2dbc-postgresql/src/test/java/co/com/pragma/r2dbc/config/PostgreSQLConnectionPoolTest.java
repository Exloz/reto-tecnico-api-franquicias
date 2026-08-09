package co.com.pragma.r2dbc.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.postgresql.client.SSLMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgreSQLConnectionPoolTest {

    @Test
    void createsConfiguredConnectionPool() {
        PostgresqlConnectionProperties properties = new PostgresqlConnectionProperties(
                "localhost",
                5432,
                "franchise",
                "franchise",
                "franchise_app",
                "secret",
                SSLMode.DISABLE,
                "",
                0,
                7,
                Duration.ofMinutes(15),
                Duration.ofSeconds(5),
                Duration.ofMinutes(60),
                Duration.ofSeconds(3));

        ConnectionPool pool = new PostgreSQLConnectionPool().connectionPool(properties);

        assertNotNull(pool);
        pool.dispose();
    }

    @Test
    void appliesConfiguredSslRootCertificate() {
        PostgresqlConnectionProperties properties = new PostgresqlConnectionProperties(
                "localhost",
                5432,
                "franchise",
                "franchise",
                "franchise_app",
                "secret",
                SSLMode.VERIFY_FULL,
                "/missing/rds-ca.pem",
                0,
                7,
                Duration.ofMinutes(15),
                Duration.ofSeconds(5),
                Duration.ofMinutes(60),
                Duration.ofSeconds(3));

        assertThrows(IllegalArgumentException.class,
                () -> new PostgreSQLConnectionPool().connectionPool(properties));
    }

    @Test
    void rejectsInvalidPoolBudgets() {
        Duration second = Duration.ofSeconds(1);

        assertThrows(IllegalArgumentException.class,
                () -> properties(null, 1, second, Duration.ofSeconds(2), second, second));
        assertThrows(IllegalArgumentException.class,
                () -> properties(-1, 1, second, Duration.ofSeconds(2), second, second));
        assertThrows(IllegalArgumentException.class,
                () -> properties(0, null, second, Duration.ofSeconds(2), second, second));
        assertThrows(IllegalArgumentException.class,
                () -> properties(0, 0, second, Duration.ofSeconds(2), second, second));
        assertThrows(IllegalArgumentException.class,
                () -> properties(2, 1, second, Duration.ofSeconds(2), second, second));
        assertThrows(IllegalArgumentException.class,
                () -> properties(0, 1, Duration.ZERO, Duration.ofSeconds(2), second, second));
        assertThrows(IllegalArgumentException.class,
                () -> properties(0, 1, second, second, second, second));
    }

    private PostgresqlConnectionProperties properties(
            Integer initialSize,
            Integer maxSize,
            Duration maxIdleTime,
            Duration maxAcquireTime,
            Duration maxLifeTime,
            Duration connectTimeout) {
        return new PostgresqlConnectionProperties(
                "localhost", 5432, "franchise", "franchise", "franchise_app", "secret",
                SSLMode.DISABLE, "", initialSize, maxSize, maxIdleTime, maxAcquireTime,
                maxLifeTime, connectTimeout);
    }
}
