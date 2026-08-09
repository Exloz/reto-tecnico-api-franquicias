package co.com.pragma.api.filter;

import co.com.pragma.api.config.ApiDeadlineProperties;
import co.com.pragma.model.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class ApiDeadlineFilter implements WebFilter {
    private final ApiDeadlineProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!exchange.getRequest().getPath().value().startsWith("/api/")) {
            return chain.filter(exchange);
        }
        return chain.filter(exchange)
                .timeout(properties.timeout())
                .onErrorMap(TimeoutException.class, ServiceUnavailableException::new);
    }
}
