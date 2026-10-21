package com.slotsync.gateway.filter;

/**
 * Unit tests for IdempotencyFilter.
 */
public class IdempotencyFilterTest {

    public static void runAllTests() {
        System.out.println("--> Running IdempotencyFilterTest...");
        testNewKeyRegistration();
        testDuplicateInProgressHandling();
        testCompletedKeyReplay();
        System.out.println("  [PASS] All IdempotencyFilter tests passed successfully!\n");
    }

    public static void testNewKeyRegistration() {
        IdempotencyFilter filter = new IdempotencyFilter();
        IdempotencyFilter.CheckResult result = filter.checkAndRegister("key-abc-1");

        if (result.status() != IdempotencyFilter.CheckStatus.NEW_REQUEST) {
            throw new AssertionError("First request with new key must have status NEW_REQUEST");
        }
    }

    public static void testDuplicateInProgressHandling() {
        IdempotencyFilter filter = new IdempotencyFilter();
        filter.checkAndRegister("key-in-flight-2");

        // Concurrent click with same key while first is in-progress
        IdempotencyFilter.CheckResult secondClick = filter.checkAndRegister("key-in-flight-2");
        if (secondClick.status() != IdempotencyFilter.CheckStatus.IN_PROGRESS) {
            throw new AssertionError("Concurrent duplicate request must be rejected with IN_PROGRESS status");
        }
    }

    public static void testCompletedKeyReplay() {
        IdempotencyFilter filter = new IdempotencyFilter();
        String key = "key-completed-3";
        filter.checkAndRegister(key);

        // First request completes
        String mockResponse = "{\"status\":\"CONFIRMED\",\"orderId\":\"ord_123\"}";
        filter.recordCompletion(key, 200, mockResponse);

        // Later retry with same key
        IdempotencyFilter.CheckResult replay = filter.checkAndRegister(key);
        if (replay.status() != IdempotencyFilter.CheckStatus.ALREADY_COMPLETED) {
            throw new AssertionError("Expected status ALREADY_COMPLETED for completed key");
        }
        if (replay.cachedResponse().statusCode() != 200) {
            throw new AssertionError("Expected cached status 200");
        }
        if (!mockResponse.equals(replay.cachedResponse().body())) {
            throw new AssertionError("Cached response body does not match");
        }
    }

    public static void main(String[] args) {
        runAllTests();
    }
}
