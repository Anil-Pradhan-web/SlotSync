package com.slotsync.lock;

/**
 * Master Test Suite Runner for SlotSync Slot Locking Service (Worker 3).
 */
public class SlotLockTestSuiteRunner {

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("        SLOTSYNC — SLOT-LOCK (WORKER 3) VERIFICATION SUITE        ");
        System.out.println("==================================================================");

        long startTime = System.currentTimeMillis();
        int testsRun = 0;
        int failures = 0;

        try {
            SlotLockEngineTest.runAllTests();
            testsRun++;

            SlotLockIntegrationTest.runAllTests();
            testsRun++;
        } catch (Throwable t) {
            failures++;
            System.err.println("\n[FAIL] SlotLock test suite failed with error:");
            t.printStackTrace();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("==================================================================");
        if (failures == 0) {
            System.out.printf("  RESULT: ALL %d SLOT-LOCK TEST SUITES PASSED (Duration: %d ms)%n", testsRun, elapsed);
            System.out.println("  VERIFIED: O(log N) RAM Seat Check, Ghost Inventory Auto-Reaper, HTTP 409 Conflicts.");
            System.out.println("==================================================================");
            System.exit(0);
        } else {
            System.err.printf("  RESULT: SLOT-LOCK TEST SUITE FAILED with %d failure(s).%n", failures);
            System.out.println("==================================================================");
            System.exit(1);
        }
    }
}
