package co.com.pragma.r2dbc.config;

import io.r2dbc.pool.ConnectionPool;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
                0,
                7,
                Duration.ofMinutes(15));

        ConnectionPool pool = new PostgreSQLConnectionPool().connectionPool(properties);

        assertNotNull(pool);
        pool.dispose();
    }
}
