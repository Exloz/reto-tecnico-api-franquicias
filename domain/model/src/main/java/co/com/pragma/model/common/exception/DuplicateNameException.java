package co.com.pragma.model.common.exception;

public class DuplicateNameException extends RuntimeException {

    public DuplicateNameException(String resource, String name) {
        super(resource + " name already exists: " + name);
    }
}
