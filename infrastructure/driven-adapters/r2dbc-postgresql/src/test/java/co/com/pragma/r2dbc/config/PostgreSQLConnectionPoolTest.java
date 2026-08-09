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
}
