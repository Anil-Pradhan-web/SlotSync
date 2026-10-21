package com.slotsync.gateway;

import com.slotsync.gateway.filter.IdempotencyFilter;
import com.slotsync.gateway.filter.RateLimitingFilter;
import com.slotsync.gateway.server.GatewayServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * End-to-End HTTP Integration Test for API Gateway.
 */
public class GatewayIntegrationTest {

    public static void runAllTests() {
        System.out.println("--> Running GatewayIntegrationTest (Real HTTP Server)...");
        testHealthCheckEndpoint();
        testEdgeRateLimitingHttp429();
        testIdempotencyKeyDeduplication();
        System.out.println("  [PASS] All GatewayIntegrationTest tests passed successfully!\n");
    }

    public static void testHealthCheckEndpoint() {
        try (GatewayServer server = new GatewayServer(0)) {
            server.start();
            int port = server.getPort();

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/health"))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AssertionError("Expected HTTP 200 from /health, got: " + response.statusCode());
            }
            if (!response.body().contains("\"status\":\"UP\"")) {
                throw new AssertionError("Health response missing 'status: UP', got: " + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Health check test failed", e);
        }
    }

    public static void testEdgeRateLimitingHttp429() {
        // Configure strict rate limit: capacity 3, refill 1/sec
        RateLimitingFilter rateLimiter = new RateLimitingFilter(3, 1);
        IdempotencyFilter idempotencyFilter = new IdempotencyFilter();

        try (GatewayServer server = new GatewayServer(0, rateLimiter, idempotencyFilter)) {
            server.start();
            int port = server.getPort();
            HttpClient client = HttpClient.newHttpClient();

            // First 3 requests must succeed (HTTP 200)
            for (int i = 0; i < 3; i++) {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/v1/queue/join"))
                    .header("X-Forwarded-For", "192.168.5.55")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    throw new AssertionError("Request " + (i + 1) + " should be HTTP 200, got: " + resp.statusCode());
                }
                if (!resp.headers().firstValue("X-Trace-Id").isPresent()) {
                    throw new AssertionError("Gateway must attach X-Trace-Id header!");
                }
            }

            // 4th burst request from same IP must be BLOCKED with HTTP 429
            HttpRequest botSpam = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/queue/join"))
                .header("X-Forwarded-For", "192.168.5.55")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            HttpResponse<String> throttledResp = client.send(botSpam, HttpResponse.BodyHandlers.ofString());
            if (throttledResp.statusCode() != 429) {
                throw new AssertionError("Expected HTTP 429 Too Many Requests for 4th click, got: " + throttledResp.statusCode());
            }
            if (!throttledResp.body().contains("RATE_LIMIT_EXCEEDED")) {
                throw new AssertionError("Error payload must indicate RATE_LIMIT_EXCEEDED: " + throttledResp.body());
            }
            if (!throttledResp.headers().firstValue("Retry-After").isPresent()) {
                throw new AssertionError("Gateway must attach Retry-After header on HTTP 429!");
            }
        } catch (Exception e) {
            throw new RuntimeException("Rate limiting integration test failed", e);
        }
    }

    public static void testIdempotencyKeyDeduplication() {
        try (GatewayServer server = new GatewayServer(0)) {
            server.start();
            int port = server.getPort();
            HttpClient client = HttpClient.newHttpClient();

            String idempKey = "test-idemp-" + System.currentTimeMillis();

            // First click
            HttpRequest req1 = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/queue/join"))
                .header("X-Forwarded-For", "192.168.10.1")
                .header("X-Idempotency-Key", idempKey)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            HttpResponse<String> resp1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
            if (resp1.statusCode() != 200) {
                throw new AssertionError("First request failed: " + resp1.statusCode());
            }

            // Accidental immediate double-click with identical key
            HttpRequest req2 = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/queue/join"))
                .header("X-Forwarded-For", "192.168.10.1")
                .header("X-Idempotency-Key", idempKey)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            HttpResponse<String> resp2 = client.send(req2, HttpResponse.BodyHandlers.ofString());
            if (resp2.statusCode() != 200) {
                throw new AssertionError("Deduplicated request should replay HTTP 200, got: " + resp2.statusCode());
            }
            if (!resp1.body().equals(resp2.body())) {
                throw new AssertionError("Deduplicated request must replay exact same response body! Got: " + resp2.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("Idempotency integration test failed", e);
        }
    }

    public static void main(String[] args) {
        runAllTests();
    }
}
