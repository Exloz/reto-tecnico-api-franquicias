package co.com.pragma.r2dbc.resilience;

import co.com.pragma.model.common.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.R2dbcTimeoutException;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

@Component
public class R2dbcFailureClassifier {

    public boolean retryRead(Throwable error) {
        return isTimeout(error) || isConnectionFailure(error) || isAbortedTransaction(error);
    }

    public boolean retryWrite(Throwable error) {
        return isConnectFailure(error) || hasSqlState(error, "40P01")
                || hasSqlState(error, "40001");
    }

    public boolean recordCircuitFailure(Throwable error) {
        return isTimeout(error) || isConnectionFailure(error) || isAbortedTransaction(error);
    }

    public boolean unavailable(Throwable error) {
        return error instanceof CallNotPermittedException || recordCircuitFailure(error);
    }

    public String reason(Throwable error) {
        if (error instanceof CallNotPermittedException) {
            return "circuit_open";
        }
        if (hasSqlState(error, "40P01")) {
            return "deadlock";
        }
        if (hasSqlState(error, "40001")) {
            return "serialization";
        }
        if (isTimeout(error)) {
            return "timeout";
        }
        if (isConnectionFailure(error)) {
            return "connection";
        }
        return "other";
    }

    public boolean domainFailure(Throwable error) {
        return error instanceof ServiceUnavailableException
                || error.getClass().getPackageName().startsWith("co.com.pragma.model.");
    }

    private boolean isTimeout(Throwable error) {
        return hasCause(error, TimeoutException.class) || hasCause(error, R2dbcTimeoutException.class);
    }

    private boolean isConnectionFailure(Throwable error) {
        return isConnectFailure(error) || hasSqlStateClass(error, "08");
    }

    private boolean isConnectFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ConnectException
                    || current.getClass().getName().equals("io.netty.channel.ConnectTimeoutException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isAbortedTransaction(Throwable error) {
        return hasSqlState(error, "40P01") || hasSqlState(error, "40001");
    }

    private boolean hasSqlState(Throwable error, String sqlState) {
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

    private boolean hasSqlStateClass(Throwable error, String sqlStateClass) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof R2dbcException r2dbcException
                    && r2dbcException.getSqlState() != null
                    && r2dbcException.getSqlState().startsWith(sqlStateClass)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
