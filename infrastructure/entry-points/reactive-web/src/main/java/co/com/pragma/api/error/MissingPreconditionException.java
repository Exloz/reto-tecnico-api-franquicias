package co.com.pragma.api.error;

public class MissingPreconditionException extends RuntimeException {

    public MissingPreconditionException(String message) {
        super(message);
    }
}
