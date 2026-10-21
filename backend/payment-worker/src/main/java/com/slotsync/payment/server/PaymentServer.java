package com.slotsync.payment.server;

import com.slotsync.payment.model.Booking;
import com.slotsync.payment.service.PaymentService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

/**
 * High-performance HTTP Server for the Payment & Settlement Worker.
 */
public class PaymentServer implements AutoCloseable {

    private final HttpServer server;
    private final PaymentService paymentService;
    private final int port;

    public PaymentServer(int port, PaymentService paymentService) throws IOException {
        this.port = port;
        this.paymentService = paymentService;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        configureRoutes();
    }

    private void configureRoutes() {
        server.createContext("/health", new HealthHandler());
        server.createContext("/api/v1/payments/webhook", new WebhookHandler());
        server.createContext("/api/v1/bookings", new BookingQueryHandler());
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
                "{\"status\":\"UP\",\"service\":\"payment-worker\",\"confirmedBookings\":%d}",
                paymentService.getRepository().count()
            );
            sendResponse(exchange, 200, json);
        }
    }

    private class WebhookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":{\"code\":\"METHOD_NOT_ALLOWED\"}}");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            String lockId = query.get("lockId");
            String userId = query.getOrDefault("userId", "user-unknown");
            int start = Integer.parseInt(query.getOrDefault("start", "0"));
            int end = Integer.parseInt(query.getOrDefault("end", "0"));
            double amount = Double.parseDouble(query.getOrDefault("amount", "1450.00"));
            String txRef = query.getOrDefault("txRef", "tx_" + System.currentTimeMillis());

            // If not in query, check request body JSON
            if (lockId == null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                    StringBuilder body = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                    String bodyStr = body.toString();
                    lockId = extractStringFromJson(bodyStr, "lockId");
                    String u = extractStringFromJson(bodyStr, "userId");
                    if (u != null) userId = u;
                    start = extractIntFromJson(bodyStr, "startSeat", 0);
                    end = extractIntFromJson(bodyStr, "endSeat", 0);
                    String tx = extractStringFromJson(bodyStr, "transactionRef");
                    if (tx != null) txRef = tx;
                }
            }

            PaymentService.SettlementResult result = paymentService.processPaymentWebhook(
                lockId, userId, start, end, amount, txRef
            );

            if (result.success()) {
                Booking b = result.booking();
                String response = String.format(
                    "{\"bookingId\":\"%s\",\"orderId\":\"%s\",\"lockId\":\"%s\",\"userId\":\"%s\",\"start\":%d,\"end\":%d,\"amount\":%.2f,\"transactionRef\":\"%s\",\"status\":\"CONFIRMED\"}",
                    b.getBookingId(), b.getOrderId(), b.getLockId(), b.getUserId(), b.getStartSeat(), b.getEndSeat(), b.getAmountPaid(), b.getTransactionRef()
                );
                sendResponse(exchange, 200, response);
            } else {
                String error = String.format(
                    "{\"error\":{\"code\":\"PAYMENT_REJECTED\",\"message\":\"%s\"}}",
                    result.message()
                );
                sendResponse(exchange, 400, error);
            }
        }
    }

    private class BookingQueryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handlePreflight(exchange)) return;
            String path = exchange.getRequestURI().getPath();
            String[] segments = path.split("/");
            if (segments.length < 5) {
                sendResponse(exchange, 400, "{\"error\":{\"code\":\"MISSING_ORDER_ID\"}}");
                return;
            }

            String orderId = segments[segments.length - 1];
            Optional<Booking> booking = paymentService.getBooking(orderId);
            if (booking.isEmpty()) {
                sendResponse(exchange, 404, "{\"error\":{\"code\":\"BOOKING_NOT_FOUND\"}}");
                return;
            }

            Booking b = booking.get();
            String response = String.format(
                "{\"bookingId\":\"%s\",\"orderId\":\"%s\",\"userId\":\"%s\",\"seats\":\"%d-%d\",\"amount\":%.2f,\"status\":\"%s\"}",
                b.getBookingId(), b.getOrderId(), b.getUserId(), b.getStartSeat(), b.getEndSeat(), b.getAmountPaid(), b.getStatus()
            );
            sendResponse(exchange, 200, response);
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
