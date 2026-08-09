package co.com.pragma.r2dbc.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class PostgreSQLConnectionPool {

    @Bean
    public ConnectionPool connectionPool(PostgresqlConnectionProperties properties) {
        PostgresqlConnectionConfiguration.Builder dbConfigurationBuilder = PostgresqlConnectionConfiguration.builder()
                .host(properties.host())
                .port(properties.port())
                .database(properties.database())
                .schema(properties.schema())
                .username(properties.username())
                .password(properties.password())
                .connectTimeout(properties.connectTimeout())
                .sslMode(properties.sslMode());

        Optional.ofNullable(properties.sslRootCert())
                .filter(rootCertificate -> !rootCertificate.isBlank())
                .ifPresent(dbConfigurationBuilder::sslRootCert);

        PostgresqlConnectionConfiguration dbConfiguration = dbConfigurationBuilder.build();

        ConnectionPoolConfiguration poolConfiguration = ConnectionPoolConfiguration.builder()
                .connectionFactory(new PostgresqlConnectionFactory(dbConfiguration))
                .name("api-postgres-connection-pool")
                .initialSize(properties.initialSize())
                .maxSize(properties.maxSize())
                .maxIdleTime(properties.maxIdleTime())
                .maxAcquireTime(properties.maxAcquireTime())
                .maxLifeTime(properties.maxLifeTime())
                .validationQuery("SELECT 1")
                .build();

        return new ConnectionPool(poolConfiguration);
    }

    @Bean
    public ReactiveTransactionManager reactiveTransactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}
