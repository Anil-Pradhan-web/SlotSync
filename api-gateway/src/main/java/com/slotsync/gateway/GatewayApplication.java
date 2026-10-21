package com.slotsync.gateway;

import com.slotsync.gateway.server.GatewayServer;

/**
 * Entry point for the SlotSync API Gateway microservice.
 */
public class GatewayApplication {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        try {
            GatewayServer server = new GatewayServer(port);
            server.start();
            System.out.println("==========================================================");
            System.out.printf("  SlotSync API Gateway (Worker 1) running on port: %d%n", port);
            System.out.println("  Endpoints available:");
            System.out.printf("    - GET  http://localhost:%d/health%n", port);
            System.out.printf("    - POST http://localhost:%d/api/v1/queue/join%n", port);
            System.out.printf("    - POST http://localhost:%d/api/v1/slots/lock%n", port);
            System.out.println("==========================================================");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down API Gateway...");
                server.stop();
            }));

        } catch (Exception e) {
            System.err.println("Failed to start API Gateway: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
