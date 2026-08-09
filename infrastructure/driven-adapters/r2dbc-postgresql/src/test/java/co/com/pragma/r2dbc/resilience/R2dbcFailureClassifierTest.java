package co.com.pragma.r2dbc.resilience;

import co.com.pragma.model.common.exception.DuplicateNameException;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.R2dbcTimeoutException;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class R2dbcFailureClassifierTest {
    private final R2dbcFailureClassifier classifier = new R2dbcFailureClassifier();

    @Test
    void retriesSafeReadAndWriteFailures() {
        R2dbcException deadlock = sqlState("40P01");
        R2dbcException serialization = sqlState("40001");
        R2dbcTimeoutException acquireTimeout = mock(R2dbcTimeoutException.class);
        RuntimeException connectFailure = new RuntimeException(new ConnectException("refused"));

        assertTrue(classifier.retryRead(deadlock));
        assertTrue(classifier.retryWrite(deadlock));
        assertTrue(classifier.retryRead(serialization));
        assertTrue(classifier.retryWrite(serialization));
        assertTrue(classifier.retryRead(acquireTimeout));
        assertFalse(classifier.retryWrite(acquireTimeout));
        assertTrue(classifier.retryRead(connectFailure));
        assertTrue(classifier.retryWrite(connectFailure));
    }

    @Test
    void doesNotRetryAmbiguousWriteTimeoutsOrConnectionLoss() {
        TimeoutException operationTimeout = new TimeoutException();
        R2dbcException connectionLoss = sqlState("08006");

        assertTrue(classifier.retryRead(operationTimeout));
        assertFalse(classifier.retryWrite(operationTimeout));
        assertTrue(classifier.retryRead(connectionLoss));
        assertFalse(classifier.retryWrite(connectionLoss));
    }

    @Test
    void excludesDomainFailuresFromCircuit() {
        DuplicateNameException duplicate = new DuplicateNameException("Franchise", "Acme");

        assertTrue(classifier.domainFailure(duplicate));
        assertFalse(classifier.recordCircuitFailure(duplicate));
        assertFalse(classifier.retryRead(duplicate));
        assertFalse(classifier.retryWrite(duplicate));
    }

    private R2dbcException sqlState(String sqlState) {
        R2dbcException error = mock(R2dbcException.class);
        when(error.getSqlState()).thenReturn(sqlState);
        return error;
    }
}
