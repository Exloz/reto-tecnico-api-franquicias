package co.com.pragma.model.common.exception;

public class InvalidVersionException extends RuntimeException {

    public InvalidVersionException(long version) {
        super("Expected version must not be negative: " + version);
    }
}
