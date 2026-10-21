package com.slotsync.gateway.server;

import com.slotsync.gateway.filter.IdempotencyFilter;
import com.slotsync.gateway.filter.RateLimitingFilter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Lightweight, high-throughput API Gateway HTTP Server.
 * 
 * Enforces edge security, bot protection, and idempotency guarantees
 * before allowing traffic into the internal booking microservices.
 */
public class GatewayServer implements AutoCloseable {

    private final HttpServer server;
    private final RateLimitingFilter rateLimiter;
    private final IdempotencyFilter idempotencyFilter;
    private final int port;

    public GatewayServer(int port, RateLimitingFilter rateLimiter, IdempotencyFilter idempotencyFilter) throws IOException {
        this.port = port;
        this.rateLimiter = rateLimiter;
        this.idempotencyFilter = idempotencyFilter;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        configureRoutes();
    }

    public GatewayServer(int port) throws IOException {
        this(port, new RateLimitingFilter(10, 5), new IdempotencyFilter());
    }

    private void configureRoutes() {
        server.createContext("/health", new HealthHandler());
        server.createContext("/api/v1/queue/join", new QueueJoinHandler());
        server.createContext("/api/v1/slots/lock", new SlotLockProxyHandler());
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    @Override
    public void close() {
        stop();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":{\"code\":\"METHOD_NOT_ALLOWED\"}}");
                return;
            }
            String response = String.format(
                "{\"status\":\"UP\",\"service\":\"api-gateway\",\"activeClients\":%d}",
                rateLimiter.getActiveClientCount()
            );
            sendResponse(exchange, 200, response);
        }
    }

    private class QueueJoinHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":{\"code\":\"METHOD_NOT_ALLOWED\"}}");
                return;
            }

            String clientIp = extractClientIp(exchange);
            String traceId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().set("X-Trace-Id", traceId);

            // Step 1: Check Rate Limit (The Bot Guard)
            RateLimitingFilter.RateLimitResult rateResult = rateLimiter.evaluate(clientIp);
            exchange.getResponseHeaders().set("X-RateLimit-Remaining", String.valueOf(rateResult.remainingTokens()));
            exchange.getResponseHeaders().set("X-RateLimit-Limit", String.valueOf(rateResult.limitCapacity()));

            if (!rateResult.allowed()) {
                exchange.getResponseHeaders().set("Retry-After", String.valueOf(rateResult.retryAfterSeconds()));
                String errorBody = "{\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests. Please slow down!\",\"retryAfterSeconds\":1}}";
                sendResponse(exchange, 429, errorBody);
                return;
            }

            // Step 2: Check Idempotency Key (Double-click protection)
            String idempotencyKey = exchange.getRequestHeaders().getFirst("X-Idempotency-Key");
            IdempotencyFilter.CheckResult idempResult = idempotencyFilter.checkAndRegister(idempotencyKey);

            if (idempResult.status() == IdempotencyFilter.CheckStatus.IN_PROGRESS) {
                String errorBody = "{\"error\":{\"code\":\"REQUEST_IN_PROGRESS\",\"message\":\"A request with this key is already processing\"}}";
                sendResponse(exchange, 409, errorBody);
                return;
            }

            if (idempResult.status() == IdempotencyFilter.CheckStatus.ALREADY_COMPLETED) {
                sendResponse(exchange, idempResult.cachedResponse().statusCode(), idempResult.cachedResponse().body());
                return;
            }

            // Step 3: Success — Generate queue ticket pass
            String ticketId = "tkt_" + UUID.randomUUID().toString().substring(0, 8);
            String successBody = String.format(
                "{\"traceId\":\"%s\",\"queueTicketId\":\"%s\",\"status\":\"QUEUED\",\"message\":\"Admitted to waiting room\"}",
                traceId, ticketId
            );

            if (idempotencyKey != null) {
                idempotencyFilter.recordCompletion(idempotencyKey, 200, successBody);
            }

            sendResponse(exchange, 200, successBody);
        }
    }

    private class SlotLockProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":{\"code\":\"METHOD_NOT_ALLOWED\"}}");
                return;
            }

            String clientIp = extractClientIp(exchange);
            RateLimitingFilter.RateLimitResult rateResult = rateLimiter.evaluate(clientIp);

            if (!rateResult.allowed()) {
                exchange.getResponseHeaders().set("Retry-After", "1");
                String errorBody = "{\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Rate limit exceeded at gateway\"}}";
                sendResponse(exchange, 429, errorBody);
                return;
            }

            String traceId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().set("X-Trace-Id", traceId);

            String response = String.format("{\"traceId\":\"%s\",\"status\":\"PASSED_GATEWAY\",\"message\":\"Forwarded to locking engine\"}", traceId);
            sendResponse(exchange, 200, response);
        }
    }

    private String extractClientIp(HttpExchange exchange) {
        String xForwardedFor = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remote = exchange.getRemoteAddress();
        return remote != null ? remote.getHostString() : "unknown-ip";
    }

    private boolean handlePreflight(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String jsonBody) throws IOException {
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
