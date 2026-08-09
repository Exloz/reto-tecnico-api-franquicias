package co.com.pragma.model.common.exception;

public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(Throwable cause) {
        super("A required service is temporarily unavailable", cause);
    }
}
