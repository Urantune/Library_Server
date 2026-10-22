package customer.apigateway;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Propagates a bounded correlation ID without introducing a domain response format. */
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {
    public static final String HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String traceId = incoming != null && incoming.matches("[a-zA-Z0-9-]{1,64}")
                ? incoming : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(HEADER, traceId);
        return chain.filter(exchange.mutate().request(request -> request.headers(
                headers -> headers.set(HEADER, traceId))).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
