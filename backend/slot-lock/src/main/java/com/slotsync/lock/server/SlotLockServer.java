package com.slotsync.lock.server;

import com.slotsync.dsa.ttl.LockNode;
import com.slotsync.lock.engine.SlotLockEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * High-performance HTTP Server for Slot & Seat Locking Service.
 */
public class SlotLockServer implements AutoCloseable {

    private final HttpServer server;
    private final SlotLockEngine engine;
    private final int port;

    public SlotLockServer(int port, SlotLockEngine engine) throws IOException {
        this.port = port;
        this.engine = engine;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        configureRoutes();
    }

    public SlotLockServer(int port, int totalSeats) throws IOException {
        this(port, new SlotLockEngine(totalSeats));
    }

    private void configureRoutes() {
        server.createContext("/health", new HealthHandler());
        server.createContext("/api/v1/slots/state", new StateHandler());
        server.createContext("/api/v1/slots/availability", new AvailabilityHandler());
        server.createContext("/api/v1/slots/lock", new LockHandler());
        server.createContext("/api/v1/slots/unlock", new UnlockHandler());
    }

    public void start() {
        server.start();
    }

    public void stop() {
        engine.close();
        server.stop(0);
    }

    @Override
    public void close() {
        stop();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public SlotLockEngine getEngine() {
        return engine;
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

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            String json = String.format(
                "{\"status\":\"UP\",\"service\":\"slot-lock\",\"availableSeats\":%d,\"totalSeats\":%d,\"ghostInventorySaved\":%d}",
                engine.getTotalAvailableSeats(), engine.getTotalSeats(), engine.getAutoReapedCount()
            );
            sendResponse(exchange, 200, json);
        }
    }

    private class StateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;

            Collection<LockNode> locks = engine.getActiveLocks();
            StringBuilder locksJson = new StringBuilder("[");
            int i = 0;
            for (LockNode lock : locks) {
                if (i++ > 0) locksJson.append(",");
                locksJson.append(String.format(
                    "{\"lockId\":\"%s\",\"userId\":\"%s\",\"startSeat\":%d,\"endSeat\":%d,\"expiresAt\":%d}",
                    lock.getLockId(), lock.getUserId(), lock.getStartSeat(), lock.getEndSeat(), lock.getExpiryTimestamp()
                ));
            }
            locksJson.append("]");

            String json = String.format(
                "{\"totalSeats\":%d,\"availableSeats\":%d,\"occupiedSeats\":%d,\"ghostSaved\":%d,\"activeLocks\":%s,\"seedBooked\":[[0,1],[2,3],[4,5]]}",
                engine.getTotalSeats(),
                engine.getTotalAvailableSeats(),
                engine.getTotalSeats() - engine.getTotalAvailableSeats(),
                engine.getAutoReapedCount(),
                locksJson.toString()
            );
            sendResponse(exchange, 200, json);
        }
    }

    private class AvailabilityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            int start = Integer.parseInt(query.getOrDefault("start", "0"));
            int end = Integer.parseInt(query.getOrDefault("end", "0"));

            int available = engine.checkAvailability(start, end);
            boolean isFree = engine.isRangeFree(start, end);

            String response = String.format(
                "{\"start\":%d,\"end\":%d,\"availableCount\":%d,\"isFree\":%b}",
                start, end, available, isFree
            );
            sendResponse(exchange, 200, response);
        }
    }

    private class LockHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":{\"code\":\"METHOD_NOT_ALLOWED\"}}");
                return;
            }

            int start = 0;
            int end = 0;
            long ttl = 300000;
            String userId = exchange.getRequestHeaders().getFirst("X-User-Id");

            // Check query params first
            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            if (query.containsKey("start")) {
                start = Integer.parseInt(query.getOrDefault("start", "0"));
                end = Integer.parseInt(query.getOrDefault("end", "0"));
                ttl = Long.parseLong(query.getOrDefault("ttl", "300000"));
            } else {
                // Parse request body JSON
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                    StringBuilder body = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                    String bodyStr = body.toString();
                    start = extractIntFromJson(bodyStr, "startSeat", 0);
                    end = extractIntFromJson(bodyStr, "endSeat", 0);
                    ttl = extractLongFromJson(bodyStr, "ttlMs", 300000L);
                    String parsedUser = extractStringFromJson(bodyStr, "userId");
                    if (parsedUser != null) userId = parsedUser;
                }
            }

            if (userId == null) userId = "passenger-" + System.currentTimeMillis();

            SlotLockEngine.LockResult result = engine.reserveSlots(userId, start, end, ttl);
            if (result.success()) {
                String response = String.format(
                    "{\"lockId\":\"%s\",\"status\":\"LOCKED\",\"start\":%d,\"end\":%d,\"expiresAt\":%d}",
                    result.lockId(), result.startSeat(), result.endSeat(), result.expiresAt()
                );
                sendResponse(exchange, 200, response);
            } else {
                String error = "{\"error\":{\"code\":\"SLOTS_UNAVAILABLE\",\"message\":\"One or more requested seats are already locked or booked\"}}";
                sendResponse(exchange, 409, error);
            }
        }
    }

    private class UnlockHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            String lockId = query.get("lockId");
            if (lockId == null) {
                sendResponse(exchange, 400, "{\"error\":{\"code\":\"MISSING_LOCK_ID\"}}");
                return;
            }

            boolean released = engine.releaseReservation(lockId);
            String response = String.format("{\"lockId\":\"%s\",\"released\":%b}", lockId, released);
            sendResponse(exchange, 200, response);
        }
    }

    private int extractIntFromJson(String json, String key, int def) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return def;
        int start = idx + pattern.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        if (end == -1) end = json.length();
        try {
            return Integer.parseInt(json.substring(start, end).replaceAll("[^0-9-]", "").trim());
        } catch (Exception e) {
            return def;
        }
    }

    private long extractLongFromJson(String json, String key, long def) {
        String pattern = "\"" + key + "\":";
        int idx = json.indexOf(pattern);
        if (idx == -1) return def;
        int start = idx + pattern.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        if (end == -1) end = json.length();
        try {
            return Long.parseLong(json.substring(start, end).replaceAll("[^0-9-]", "").trim());
        } catch (Exception e) {
            return def;
        }
    }

    private String extractStringFromJson(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;
        int start = idx + pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    private Map<String, String> parseQuery(String queryStr) {
        Map<String, String> map = new HashMap<>();
        if (queryStr != null) {
            for (String pair : queryStr.split("&")) {
                String[] parts = pair.split("=");
                if (parts.length == 2) {
                    map.put(parts[0], parts[1]);
                }
            }
        }
        return map;
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
