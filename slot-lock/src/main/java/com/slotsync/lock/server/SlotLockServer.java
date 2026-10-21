package com.slotsync.lock.server;

import com.slotsync.lock.engine.SlotLockEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = String.format(
                "{\"status\":\"UP\",\"service\":\"slot-lock\",\"availableSeats\":%d,\"totalSeats\":%d}",
                engine.getTotalAvailableSeats(), engine.getTotalSeats()
            );
            sendResponse(exchange, 200, json);
        }
    }

    private class AvailabilityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
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
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":{\"code\":\"METHOD_NOT_ALLOWED\"}}");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            int start = Integer.parseInt(query.getOrDefault("start", "0"));
            int end = Integer.parseInt(query.getOrDefault("end", "0"));
            long ttl = Long.parseLong(query.getOrDefault("ttl", "300000")); // default 5 min
            String userId = exchange.getRequestHeaders().getFirst("X-User-Id");
            if (userId == null) userId = "anon-user";

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
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
