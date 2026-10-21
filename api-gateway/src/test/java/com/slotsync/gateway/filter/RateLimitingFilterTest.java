package com.slotsync.gateway.filter;

/**
 * Unit tests for RateLimitingFilter.
 */
public class RateLimitingFilterTest {

    public static void runAllTests() {
        System.out.println("--> Running RateLimitingFilterTest...");
        testSingleClientBurstAndThrottle();
        testMultipleClientsIsolation();
        System.out.println("  [PASS] All RateLimitingFilter tests passed successfully!\n");
    }

    public static void testSingleClientBurstAndThrottle() {
        RateLimitingFilter filter = new RateLimitingFilter(5, 1);
        String clientIp = "192.168.1.100";

        // First 5 requests must be allowed (burst)
        for (int i = 0; i < 5; i++) {
            RateLimitingFilter.RateLimitResult result = filter.evaluate(clientIp);
            if (!result.allowed()) {
                throw new AssertionError("Expected request " + (i + 1) + " to be allowed within capacity 5");
            }
        }

        // 6th request must be throttled
        RateLimitingFilter.RateLimitResult throttled = filter.evaluate(clientIp);
        if (throttled.allowed()) {
            throw new AssertionError("Expected 6th request from same IP to be throttled with HTTP 429!");
        }
        if (throttled.retryAfterSeconds() <= 0) {
            throw new AssertionError("Expected retryAfterSeconds to be positive when throttled");
        }
    }

    public static void testMultipleClientsIsolation() {
        RateLimitingFilter filter = new RateLimitingFilter(3, 1);
        String badBotIp = "10.0.0.1";
        String goodUserIp = "10.0.0.2";

        // Bad bot uses up all 3 tokens and gets throttled
        for (int i = 0; i < 3; i++) {
            filter.evaluate(badBotIp);
        }
        if (filter.evaluate(badBotIp).allowed()) {
            throw new AssertionError("Bad bot should be throttled on 4th request");
        }

        // Good user must NOT be affected by bad bot!
        RateLimitingFilter.RateLimitResult goodResult = filter.evaluate(goodUserIp);
        if (!goodResult.allowed()) {
            throw new AssertionError("Good user must be allowed! Separate IP buckets must be isolated.");
        }
    }

    public static void main(String[] args) {
        runAllTests();
    }
}
