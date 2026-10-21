package com.slotsync.waitingroom;

import com.slotsync.waitingroom.dispatcher.BatchAdmissionDispatcher;
import com.slotsync.waitingroom.server.WaitingRoomServer;
import com.slotsync.waitingroom.service.QueueService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * End-to-End HTTP Integration Test for Virtual Waiting Room.
 */
public class WaitingRoomIntegrationTest {

    public static void runAllTests() {
        System.out.println("--> Running WaitingRoomIntegrationTest (Real HTTP Server)...");
        testWaitingRoomJoinAndAdmission();
        System.out.println("  [PASS] All WaitingRoomIntegrationTest tests passed successfully!\n");
    }

    public static void testWaitingRoomJoinAndAdmission() {
        QueueService queueService = new QueueService();
        BatchAdmissionDispatcher dispatcher = new BatchAdmissionDispatcher(queueService, 10, 60_000L, null);

        try (WaitingRoomServer server = new WaitingRoomServer(0, queueService, dispatcher)) {
            server.start();
            int port = server.getPort();
            HttpClient client = HttpClient.newHttpClient();

            // Step 1: Health check
            HttpRequest healthReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/health"))
                .GET()
                .build();
            HttpResponse<String> healthResp = client.send(healthReq, HttpResponse.BodyHandlers.ofString());
            if (healthResp.statusCode() != 200 || !healthResp.body().contains("\"status\":\"UP\"")) {
                throw new AssertionError("Health check failed: " + healthResp.body());
            }

            // Step 2: User joins queue
            HttpRequest joinReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/queue/join"))
                .header("X-User-Id", "rahul-tatkal-buyer")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            HttpResponse<String> joinResp = client.send(joinReq, HttpResponse.BodyHandlers.ofString());
            if (joinResp.statusCode() != 200) {
                throw new AssertionError("Queue join failed with status: " + joinResp.statusCode());
            }

            // Extract ticketId from JSON response: {"ticketId":"tkt_xxxx",...}
            String body = joinResp.body();
            String ticketId = body.split("\"ticketId\":\"")[1].split("\"")[0];

            // Step 3: Check status -> should be WAITING
            HttpRequest statusReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/queue/status/" + ticketId))
                .GET()
                .build();

            HttpResponse<String> statusResp = client.send(statusReq, HttpResponse.BodyHandlers.ofString());
            if (!statusResp.body().contains("\"status\":\"WAITING\"")) {
                throw new AssertionError("Expected status WAITING initially, got: " + statusResp.body());
            }

            // Step 4: Dispatcher admits batch
            dispatcher.dispatchBatch();

            // Step 5: Check status again -> should be ADMITTED with admissionToken
            HttpResponse<String> admittedResp = client.send(statusReq, HttpResponse.BodyHandlers.ofString());
            if (!admittedResp.body().contains("\"status\":\"ADMITTED\"")) {
                throw new AssertionError("Expected status ADMITTED after batch dispatch, got: " + admittedResp.body());
            }
            if (!admittedResp.body().contains("\"admissionToken\":\"adm_")) {
                throw new AssertionError("Admission token missing in response: " + admittedResp.body());
            }

        } catch (Exception e) {
            throw new RuntimeException("Waiting room integration test failed", e);
        }
    }

    public static void main(String[] args) {
        runAllTests();
    }
}
