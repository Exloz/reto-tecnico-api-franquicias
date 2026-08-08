package co.com.pragma.r2dbc;

import io.r2dbc.postgresql.api.ErrorDetails;
import io.r2dbc.postgresql.api.PostgresqlException;
import io.r2dbc.spi.R2dbcException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class PostgresqlErrorMapperTest {

    @Test
    void findsSqlStateInCauseChain() {
        R2dbcException cause = mock(R2dbcException.class);
        when(cause.getSqlState()).thenReturn("23505");

        assertTrue(PostgresqlErrorMapper.hasSqlState(new RuntimeException(cause), "23505"));
        assertFalse(PostgresqlErrorMapper.hasSqlState(new RuntimeException(cause), "23503"));
        assertFalse(PostgresqlErrorMapper.hasSqlState(new RuntimeException(), "23505"));
    }

    @Test
    void findsConstraintInCauseChain() {
        R2dbcException cause = mock(
                R2dbcException.class,
                withSettings().extraInterfaces(PostgresqlException.class));
        ErrorDetails details = mock(ErrorDetails.class);
        when(((PostgresqlException) cause).getErrorDetails()).thenReturn(details);
        when(details.getConstraintName()).thenReturn(java.util.Optional.of("uq_name"));

        assertTrue(PostgresqlErrorMapper.hasConstraint(new RuntimeException(cause), "uq_name"));
        assertFalse(PostgresqlErrorMapper.hasConstraint(new RuntimeException(cause), "other"));
    }
}
