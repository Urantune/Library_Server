package customer.apigateway;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@Component
public class UserJwtRbacFilter implements GlobalFilter, Ordered {
    private final ObjectMapper mapper;
    private final Resource publicKeyResource;
    private final String issuer;
    private final String audience;

    public UserJwtRbacFilter(ObjectMapper mapper,
            @Value("${security.jwt.public-key:}") Resource publicKeyResource,
            @Value("${security.jwt.issuer:}") String issuer,
            @Value("${security.jwt.audience:}") String audience) {
        this.mapper = mapper;
        this.publicKeyResource = publicKeyResource;
        this.issuer = issuer;
        this.audience = audience;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getRawPath();
        if (!(path.startsWith("/api/users") || path.startsWith("/identity/api/users"))) return chain.filter(exchange);

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        Claims claims;
        try {
            claims = verify(authorization);
        } catch (Exception exception) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        boolean profileRead = path.endsWith("/me") && (request.getMethod().name().equals("GET") || request.getMethod().name().equals("PUT"));
        boolean adminOperation = claims.roles().contains("ADMIN")
                && (path.endsWith("/users") || path.matches(".*/users/[^/]+(/status)?$")
                && List.of("GET", "PUT", "PATCH", "DELETE").contains(request.getMethod().name())
                || request.getMethod().name().equals("POST") && path.endsWith("/users"));
        if (!profileRead && !adminOperation) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        ServerHttpRequest trusted = request.mutate().headers(headers -> {
            headers.remove("X-User-Id");
            headers.remove("X-User-Roles");
            headers.set("X-User-Id", claims.subject());
            headers.set("X-User-Roles", String.join(",", claims.roles()));
        }).build();
        return chain.filter(exchange.mutate().request(trusted).build());
    }

    private Claims verify(String authorization) throws Exception {
        if (authorization == null || !authorization.startsWith("Bearer ") || !publicKeyResource.exists()) throw new SecurityException();
        String[] parts = authorization.substring(7).split("\\.");
        if (parts.length != 3) throw new SecurityException();
        Map<String, Object> header = mapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
        if (!"RS256".equals(header.get("alg"))) throw new SecurityException();
        String pem = StreamUtils.copyToString(publicKeyResource.getInputStream(), StandardCharsets.UTF_8)
                .replaceAll("-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\\s", "");
        var key = java.security.KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!verifier.verify(Base64.getUrlDecoder().decode(parts[2]))) throw new SecurityException();
        Map<String, Object> payload = mapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
        if (!(payload.get("sub") instanceof String subject)
                || !(payload.get("exp") instanceof Number exp) || exp.longValue() <= Instant.now().getEpochSecond()) throw new SecurityException();
        if (issuer.isBlank() || audience.isBlank() || !issuer.equals(payload.get("iss"))) throw new SecurityException();
        Object tokenAudience = payload.get("aud");
        if (!(tokenAudience instanceof String value && audience.equals(value))
                && !(tokenAudience instanceof Collection<?> values && values.contains(audience))) throw new SecurityException();
        Object rawRoles = payload.get("roles");
        List<String> roles = rawRoles instanceof Collection<?> collection
                ? collection.stream().map(String::valueOf).toList()
                : rawRoles instanceof String role ? List.of(role) : List.of();
        return new Claims(subject, roles);
    }

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
    private record Claims(String subject, List<String> roles) {}
}
