package co.com.pragma.r2dbc;

import io.r2dbc.postgresql.api.PostgresqlException;
import io.r2dbc.spi.R2dbcException;

public final class PostgresqlErrorMapper {

    private PostgresqlErrorMapper() {
    }

    public static boolean hasSqlState(Throwable error, String sqlState) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof R2dbcException r2dbcException
                    && sqlState.equals(r2dbcException.getSqlState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static boolean hasConstraint(Throwable error, String constraintName) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof PostgresqlException postgresqlException
                    && postgresqlException.getErrorDetails()
                    .getConstraintName()
                    .filter(constraintName::equals)
                    .isPresent()) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
