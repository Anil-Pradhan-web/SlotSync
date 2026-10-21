package com.slotsync.dsa;

import com.slotsync.dsa.hashring.ConsistentHashRingTest;
import com.slotsync.dsa.ratelimiter.TokenBucketTest;
import com.slotsync.dsa.segmenttree.SegmentTreeTest;
import com.slotsync.dsa.ttl.MinHeapTTLReaperTest;

/**
 * Master Test Suite Runner for SlotSync DSA Engine.
 * Executes all 4 algorithm test suites and verifies invariants.
 */
public class DsaTestSuiteRunner {

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("       SLOTSYNC — CORE DSA ENGINE VERIFICATION SUITE              ");
        System.out.println("==================================================================");

        long startTime = System.currentTimeMillis();
        int testsRun = 0;
        int failures = 0;

        try {
            // 1. TokenBucket tests
            TokenBucketTest.runAllTests();
            testsRun++;

            // 2. SegmentTree tests
            SegmentTreeTest.runAllTests();
            testsRun++;

            // 3. MinHeapTTLReaper tests
            MinHeapTTLReaperTest.runAllTests();
            testsRun++;

            // 4. ConsistentHashRing tests
            ConsistentHashRingTest.runAllTests();
            testsRun++;

        } catch (Throwable t) {
            failures++;
            System.err.println("\n[FAIL] Test suite failed with error:");
            t.printStackTrace();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("==================================================================");
        if (failures == 0) {
            System.out.printf("  RESULT: ALL %d DSA TEST SUITES PASSED (Duration: %d ms)%n", testsRun, elapsed);
            System.out.println("  VERIFIED: Zero Over-booking, Zero Ghost Inventory, Safe Bursts.");
            System.out.println("==================================================================");
            System.exit(0);
        } else {
            System.err.printf("  RESULT: TEST SUITE FAILED with %d failure(s).%n", failures);
            System.out.println("==================================================================");
            System.exit(1);
        }
    }
}
