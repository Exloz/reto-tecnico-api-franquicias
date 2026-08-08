package co.com.pragma.api.dto;

public final class ApiRequests {

    private ApiRequests() {
    }

    public record Name(String name) {
    }

    public record CreateProduct(String name, Integer stock) {
    }

    public record Stock(Integer stock) {
    }
}
