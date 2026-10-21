package com.slotsync.gateway;

import com.slotsync.gateway.filter.IdempotencyFilterTest;
import com.slotsync.gateway.filter.RateLimitingFilterTest;

/**
 * Master Test Suite Runner for SlotSync API Gateway (Worker 1).
 */
public class GatewayTestSuiteRunner {

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("       SLOTSYNC — API GATEWAY (WORKER 1) VERIFICATION SUITE       ");
        System.out.println("==================================================================");

        long startTime = System.currentTimeMillis();
        int testsRun = 0;
        int failures = 0;

        try {
            // 1. Rate Limiting Filter Tests
            RateLimitingFilterTest.runAllTests();
            testsRun++;

            // 2. Idempotency Filter Tests
            IdempotencyFilterTest.runAllTests();
            testsRun++;

            // 3. Real HTTP Gateway Integration Tests
            GatewayIntegrationTest.runAllTests();
            testsRun++;

        } catch (Throwable t) {
            failures++;
            System.err.println("\n[FAIL] Gateway test suite failed with error:");
            t.printStackTrace();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("==================================================================");
        if (failures == 0) {
            System.out.printf("  RESULT: ALL %d GATEWAY TEST SUITES PASSED (Duration: %d ms)%n", testsRun, elapsed);
            System.out.println("  VERIFIED: HTTP 429 Bot Throttling, Idempotency Deduplication, Traceability.");
            System.out.println("==================================================================");
            System.exit(0);
        } else {
            System.err.printf("  RESULT: GATEWAY TEST SUITE FAILED with %d failure(s).%n", failures);
            System.out.println("==================================================================");
            System.exit(1);
        }
    }
}
