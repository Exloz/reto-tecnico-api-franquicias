package co.com.pragma.model.common.exception;

public class InvalidStockException extends RuntimeException {

    public InvalidStockException(int stock) {
        super("Stock must not be negative: " + stock);
    }
}
