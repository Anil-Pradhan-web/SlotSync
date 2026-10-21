package com.slotsync.waitingroom.server;

import com.slotsync.waitingroom.dispatcher.BatchAdmissionDispatcher;
import com.slotsync.waitingroom.model.QueueTicket;
import com.slotsync.waitingroom.service.QueueService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * High-throughput HTTP Server for the Virtual Waiting Room microservice.
 */
public class WaitingRoomServer implements AutoCloseable {

    private final HttpServer server;
    private final QueueService queueService;
    private final BatchAdmissionDispatcher dispatcher;
    private final int port;

    public WaitingRoomServer(int port, QueueService queueService, BatchAdmissionDispatcher dispatcher) throws IOException {
        this.port = port;
        this.queueService = queueService;
        this.dispatcher = dispatcher;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        configureRoutes();
    }

    public WaitingRoomServer(int port, int batchSize) throws IOException {
        this(port, new QueueService(), null);
    }

    private void configureRoutes() {
        server.createContext("/health", new HealthHandler());
        server.createContext("/api/v1/queue/join", new JoinHandler());
        server.createContext("/api/v1/queue/status", new StatusHandler());
    }

    public void start() {
        if (dispatcher != null && !dispatcher.isRunning()) {
            dispatcher.start();
        }
        server.start();
    }

    public void stop() {
        if (dispatcher != null) {
            dispatcher.close();
        }
        server.stop(0);
    }

    @Override
    public void close() {
        stop();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public QueueService getQueueService() {
        return queueService;
    }

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = String.format(
                "{\"status\":\"UP\",\"service\":\"waiting-room\",\"waitingUsers\":%d,\"totalAdmitted\":%d}",
                queueService.getWaitingCount(), queueService.getTotalAdmittedCount()
            );
            sendResponse(exchange, 200, json);
        }
    }

    private class JoinHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":{\"code\":\"METHOD_NOT_ALLOWED\"}}");
                return;
            }

            String userId = exchange.getRequestHeaders().getFirst("X-User-Id");
            if (userId == null || userId.isBlank()) {
                userId = "usr_" + System.currentTimeMillis();
            }

            QueueTicket ticket = queueService.enqueue(userId, 101L);
            int position = queueService.getQueuePosition(ticket.getTicketId());
            long estimatedWaitSeconds = (position / 200) + 1;

            String response = String.format(
                "{\"ticketId\":\"%s\",\"userId\":\"%s\",\"status\":\"WAITING\",\"position\":%d,\"estimatedWaitSeconds\":%d}",
                ticket.getTicketId(), ticket.getUserId(), position, estimatedWaitSeconds
            );
            sendResponse(exchange, 200, response);
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String[] segments = path.split("/");
            if (segments.length < 5) {
                sendResponse(exchange, 400, "{\"error\":{\"code\":\"MISSING_TICKET_ID\"}}");
                return;
            }

            String ticketId = segments[segments.length - 1];
            QueueTicket ticket = queueService.getTicket(ticketId);
            if (ticket == null) {
                sendResponse(exchange, 404, "{\"error\":{\"code\":\"TICKET_NOT_FOUND\"}}");
                return;
            }

            if (ticket.getStatus() == QueueTicket.Status.ADMITTED) {
                String response = String.format(
                    "{\"ticketId\":\"%s\",\"status\":\"ADMITTED\",\"admissionToken\":\"%s\",\"expiresAt\":%d}",
                    ticket.getTicketId(), ticket.getAdmissionToken(), ticket.getAdmissionExpiresAt()
                );
                sendResponse(exchange, 200, response);
            } else {
                int position = queueService.getQueuePosition(ticket.getTicketId());
                String response = String.format(
                    "{\"ticketId\":\"%s\",\"status\":\"WAITING\",\"position\":%d}",
                    ticket.getTicketId(), position
                );
                sendResponse(exchange, 200, response);
            }
        }
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
