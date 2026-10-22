package customer.apigateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTests {
    private static final HttpServer DOWNSTREAM = startDownstream();
    @LocalServerPort
    private int port;
    private final HttpClient client = HttpClient.newHttpClient();

    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String body = exchange.getRequestURI() + "|" + exchange.getRequestHeaders().getFirst("X-Trace-Id");
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            server.start();
            return server;
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        for (String service : new String[]{"IDENTITY", "CATALOG", "CIRCULATION", "RESERVATION", "NOTIFICATION"}) {
            registry.add(service + "_SERVICE_URI",
                    () -> "http://127.0.0.1:" + DOWNSTREAM.getAddress().getPort());
        }
    }

    @AfterAll
    static void stopDownstream() { DOWNSTREAM.stop(0); }

    private HttpResponse<String> get(String path, String trace) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Trace-Id", trace).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void routesEveryServiceAndPreservesQueryAndTrace() throws Exception {
        for (String service : new String[]{"identity", "catalog", "circulation", "reservation", "notification"}) {
            var response = get("/api/" + service + "/probe?page=2", "test-trace-123");
            assertEquals(200, response.statusCode());
            assertEquals("/probe?page=2|test-trace-123", response.body());
            assertEquals("test-trace-123", response.headers().firstValue("X-Trace-Id").orElseThrow());
        }
    }

    @Test
    void keepsLegacyAliasAndReplacesInvalidTrace() throws Exception {
        var response = get("/catalog/probe", "invalid trace");
        assertEquals(200, response.statusCode());
        String trace = response.headers().firstValue("X-Trace-Id").orElseThrow();
        assertDoesNotThrow(() -> java.util.UUID.fromString(trace));
        assertEquals("/probe|" + trace, response.body());
    }

    @Test
    void corsPreflightAllowsConfiguredOrigin() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/catalog/probe"))
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("http://localhost:3000", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
    }

    @Test
    void exposesReadinessAndMetricsAndRejectsUnknownRoute() throws Exception {
        assertEquals(200, get("/actuator/health/readiness", "health").statusCode());
        assertEquals(200, get("/actuator/metrics", "metrics").statusCode());
        assertEquals(200, get("/actuator/prometheus", "metrics").statusCode());
        assertEquals(404, get("/api/unknown/probe", "missing").statusCode());
    }
}

