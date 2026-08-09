package co.com.pragma.api.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestObservabilityFilter implements WebFilter {
    public static final String API_GATEWAY_REQUEST_ID_HEADER = "X-Api-Gateway-Request-Id";
    public static final String ERROR_CODE_ATTRIBUTE = RequestObservabilityFilter.class.getName() + ".errorCode";
    public static final String OPERATION_ATTRIBUTE = RequestObservabilityFilter.class.getName() + ".operation";
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestObservabilityFilter.class);
    private static final String SAFE_IDENTIFIER = "[\\x20-\\x7E]{1,128}";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startedAt = System.nanoTime();
        exchange.getResponse().beforeCommit(() -> Mono.fromRunnable(() -> logCompletion(exchange, startedAt)));
        return chain.filter(exchange);
    }

    private void logCompletion(ServerWebExchange exchange, long startedAt) {
        int status = Optional.ofNullable(exchange.getResponse().getStatusCode())
                .map(HttpStatusCode::value)
                .orElse(200);
        String outcome = Optional.of(status)
                .filter(value -> value < 400)
                .map(ignored -> "success")
                .or(() -> Optional.of(status)
                        .filter(value -> value < 500)
                        .map(ignored -> "client_error"))
                .orElse("server_error");
        LOGGER.atInfo()
                .addKeyValue("event", "request.completed")
                .addKeyValue("correlationId", attribute(exchange, CorrelationIdFilter.ATTRIBUTE_NAME, "unknown"))
                .addKeyValue("apiGatewayRequestId", apiGatewayRequestId(exchange))
                .addKeyValue("method", exchange.getRequest().getMethod().name())
                .addKeyValue("route", attribute(exchange, HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "unmatched"))
                .addKeyValue("operation", attribute(exchange, OPERATION_ATTRIBUTE, "unmatched"))
                .addKeyValue("status", status)
                .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
                .addKeyValue("outcome", outcome)
                .addKeyValue("errorCode", attribute(exchange, ERROR_CODE_ATTRIBUTE, "none"))
                .log("HTTP request completed");
    }

    private String apiGatewayRequestId(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getRequest().getHeaders().getFirst(API_GATEWAY_REQUEST_ID_HEADER))
                .filter(value -> value.matches(SAFE_IDENTIFIER))
                .orElse("unknown");
    }

    private String attribute(ServerWebExchange exchange, String name, String fallback) {
        return Optional.ofNullable(exchange.getAttribute(name))
                .map(Object::toString)
                .orElse(fallback);
    }
}
