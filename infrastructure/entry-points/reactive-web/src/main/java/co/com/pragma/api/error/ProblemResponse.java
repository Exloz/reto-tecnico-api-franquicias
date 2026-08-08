package co.com.pragma.api.error;

import java.net.URI;

public record ProblemResponse(
        URI type,
        String title,
        int status,
        String detail,
        URI instance,
        String correlationId) {
}
