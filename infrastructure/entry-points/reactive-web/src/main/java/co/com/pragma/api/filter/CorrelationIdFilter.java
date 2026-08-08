package co.com.pragma.api.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements WebFilter {
    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String ATTRIBUTE_NAME = CorrelationIdFilter.class.getName() + ".correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = Optional.ofNullable(exchange.getRequest().getHeaders().get(HEADER_NAME))
                .filter(values -> values.size() == 1)
                .map(List::getFirst)
                .filter(value -> value.matches("[\\x20-\\x7E]{1,128}"))
                .orElseGet(CorrelationIdFilter::newCorrelationId);
        exchange.getAttributes().put(ATTRIBUTE_NAME, correlationId);
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    public static String newCorrelationId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long mostSignificantBits = (random.nextLong() & 0xffffffffffff0fffL) | 0x0000000000004000L;
        long leastSignificantBits = (random.nextLong() & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new UUID(mostSignificantBits, leastSignificantBits).toString();
    }
}
