package com.slotsync.payment.server;

import com.slotsync.payment.model.Booking;
import com.slotsync.payment.service.PaymentService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
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

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
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
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":{\"code\":\"METHOD_NOT_ALLOWED\"}}");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI().getQuery());
            String lockId = query.get("lockId");
            String userId = query.getOrDefault("userId", "user-unknown");
            int start = Integer.parseInt(query.getOrDefault("start", "0"));
            int end = Integer.parseInt(query.getOrDefault("end", "0"));
            double amount = Double.parseDouble(query.getOrDefault("amount", "1250.00"));
            String txRef = query.getOrDefault("txRef", "tx_" + System.currentTimeMillis());

            PaymentService.SettlementResult result = paymentService.processPaymentWebhook(
                lockId, userId, start, end, amount, txRef
            );

            if (result.success()) {
                Booking b = result.booking();
                String response = String.format(
                    "{\"bookingId\":\"%s\",\"orderId\":\"%s\",\"lockId\":\"%s\",\"userId\":\"%s\",\"start\":%d,\"end\":%d,\"status\":\"CONFIRMED\"}",
                    b.getBookingId(), b.getOrderId(), b.getLockId(), b.getUserId(), b.getStartSeat(), b.getEndSeat()
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
