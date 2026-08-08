package co.com.pragma.model.common.exception;

public class InvalidPageSizeException extends RuntimeException {

    public InvalidPageSizeException(int limit, int maximum) {
        super("Page limit must be between 1 and " + maximum + ": " + limit);
    }
}
