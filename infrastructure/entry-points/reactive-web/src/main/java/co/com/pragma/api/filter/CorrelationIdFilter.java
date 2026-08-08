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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements WebFilter {
    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String ATTRIBUTE_NAME = CorrelationIdFilter.class.getName() + ".correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Mono<String> correlationId = Mono.justOrEmpty(Optional.ofNullable(
                        exchange.getRequest().getHeaders().get(HEADER_NAME))
                .filter(values -> values.size() == 1)
                .map(List::getFirst)
                .filter(value -> value.matches("[\\x20-\\x7E]{1,128}")))
                .switchIfEmpty(Mono.fromSupplier(CorrelationIdFilter::newCorrelationId));
        return correlationId.flatMap(value -> {
            exchange.getAttributes().put(ATTRIBUTE_NAME, value);
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().getHeaders().set(HEADER_NAME, value);
                return Mono.empty();
            });
            return chain.filter(exchange);
        });
    }

    public static String newCorrelationId() {
        return UUID.randomUUID().toString();
    }
}
