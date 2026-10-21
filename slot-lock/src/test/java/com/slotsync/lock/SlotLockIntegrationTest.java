package com.slotsync.lock;

import com.slotsync.lock.engine.SlotLockEngine;
import com.slotsync.lock.server.SlotLockServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * End-to-End HTTP Integration Test for SlotLockServer.
 */
public class SlotLockIntegrationTest {

    public static void runAllTests() {
        System.out.println("--> Running SlotLockIntegrationTest (Real HTTP Server)...");
        testSlotLockHttpLifecycle();
        System.out.println("  [PASS] All SlotLockIntegrationTest tests passed successfully!\n");
    }

    public static void testSlotLockHttpLifecycle() {
        try (SlotLockServer server = new SlotLockServer(0, 100)) {
            server.start();
            int port = server.getPort();
            HttpClient client = HttpClient.newHttpClient();

            // 1. Check Availability
            HttpRequest availReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/slots/availability?start=10&end=14"))
                .GET()
                .build();
            HttpResponse<String> availResp = client.send(availReq, HttpResponse.BodyHandlers.ofString());
            if (!availResp.body().contains("\"availableCount\":5") || !availResp.body().contains("\"isFree\":true")) {
                throw new AssertionError("Availability response invalid: " + availResp.body());
            }

            // 2. Lock Seats 10 to 14
            HttpRequest lockReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/slots/lock?start=10&end=14&ttl=300000"))
                .header("X-User-Id", "amit-buyer")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            HttpResponse<String> lockResp = client.send(lockReq, HttpResponse.BodyHandlers.ofString());
            if (lockResp.statusCode() != 200 || !lockResp.body().contains("\"status\":\"LOCKED\"")) {
                throw new AssertionError("Lock request failed: " + lockResp.body());
            }

            String lockId = lockResp.body().split("\"lockId\":\"")[1].split("\"")[0];

            // 3. Competing user tries to book overlapping seat 12 -> Expect HTTP 409 Conflict!
            HttpRequest conflictReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/slots/lock?start=12&end=16"))
                .header("X-User-Id", "competing-buyer")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            HttpResponse<String> conflictResp = client.send(conflictReq, HttpResponse.BodyHandlers.ofString());
            if (conflictResp.statusCode() != 409) {
                throw new AssertionError("Expected HTTP 409 Conflict for overlapping seat lock, got: " + conflictResp.statusCode());
            }

            // 4. Voluntary Unlock
            HttpRequest unlockReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/slots/unlock?lockId=" + lockId))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            HttpResponse<String> unlockResp = client.send(unlockReq, HttpResponse.BodyHandlers.ofString());
            if (!unlockResp.body().contains("\"released\":true")) {
                throw new AssertionError("Unlock failed: " + unlockResp.body());
            }

            // 5. Verify seats 10 to 14 are free again
            HttpResponse<String> recheckResp = client.send(availReq, HttpResponse.BodyHandlers.ofString());
            if (!recheckResp.body().contains("\"isFree\":true")) {
                throw new AssertionError("Seats should be free again after unlock: " + recheckResp.body());
            }

        } catch (Exception e) {
            throw new RuntimeException("Slot lock HTTP lifecycle test failed", e);
        }
    }

    public static void main(String[] args) {
        runAllTests();
    }
}
