package co.com.pragma.api.pagination;

import co.com.pragma.api.error.InvalidRequestException;
import co.com.pragma.model.branchproducts.TopStockCursor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Component
public class CursorCodec {

    public Mono<Optional<TopStockCursor>> decode(Optional<String> cursor) {
        return Mono.just(cursor)
                .flatMap(value -> value
                        .map(encoded -> Mono.fromCallable(() -> Optional.of(decodeValue(encoded)))
                                .onErrorMap(error -> new InvalidRequestException("cursor is invalid")))
                        .orElseGet(() -> Mono.just(Optional.empty())));
    }

    public String encode(TopStockCursor cursor) {
        String value = cursor.branchNormalizedName() + ':' + cursor.branchId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private TopStockCursor decodeValue(String encoded) {
        String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        int separator = value.length() - 37;
        if (separator < 1 || value.charAt(separator) != ':') {
            throw new IllegalArgumentException();
        }
        return new TopStockCursor(
                value.substring(0, separator),
                UUID.fromString(value.substring(separator + 1)));
    }
}
