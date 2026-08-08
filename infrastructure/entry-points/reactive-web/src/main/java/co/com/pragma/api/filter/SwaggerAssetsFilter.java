package co.com.pragma.api.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Component
public class SwaggerAssetsFilter implements WebFilter {
    private final boolean enabled;

    public SwaggerAssetsFilter(@Value("${api.swagger-ui.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.just(exchange)
                .filter(value -> enabled || !value.getRequest().getPath().value().startsWith("/webjars/swagger-ui/"))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .flatMap(chain::filter);
    }
}
