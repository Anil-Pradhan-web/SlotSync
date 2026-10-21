package com.slotsync.waitingroom;

/**
 * Master Test Suite Runner for SlotSync Waiting Room (Worker 2).
 */
public class WaitingRoomTestSuiteRunner {

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("      SLOTSYNC — WAITING ROOM (WORKER 2) VERIFICATION SUITE       ");
        System.out.println("==================================================================");

        long startTime = System.currentTimeMillis();
        int testsRun = 0;
        int failures = 0;

        try {
            QueueServiceTest.runAllTests();
            testsRun++;

            WaitingRoomIntegrationTest.runAllTests();
            testsRun++;
        } catch (Throwable t) {
            failures++;
            System.err.println("\n[FAIL] Waiting room test suite failed with error:");
            t.printStackTrace();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("==================================================================");
        if (failures == 0) {
            System.out.printf("  RESULT: ALL %d WAITING ROOM TEST SUITES PASSED (Duration: %d ms)%n", testsRun, elapsed);
            System.out.println("  VERIFIED: 200 users/sec batch admission, FIFO Queue, Token emission.");
            System.out.println("==================================================================");
            System.exit(0);
        } else {
            System.err.printf("  RESULT: WAITING ROOM TEST SUITE FAILED with %d failure(s).%n", failures);
            System.out.println("==================================================================");
            System.exit(1);
        }
    }
}
