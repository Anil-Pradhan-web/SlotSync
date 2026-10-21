package com.slotsync.dsa.segmenttree;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit & Concurrency Tests for SegmentTree.
 */
public class SegmentTreeTest {

    public static void runAllTests() {
        System.out.println("--> Running SegmentTreeTest...");
        testInitialStateAndRangeQuery();
        testAtomicLockAndConflict();
        testReleaseAndAvailabilityRestoration();
        testConcurrentSeatLockRace();
        System.out.println("  [PASS] All SegmentTree tests passed successfully!\n");
    }

    public static void testInitialStateAndRangeQuery() {
        final int totalSeats = 1000;
        SegmentTree tree = new SegmentTree(totalSeats);

        if (tree.getTotalAvailable() != totalSeats) {
            throw new AssertionError("Expected all " + totalSeats + " seats to be available");
        }
        if (tree.queryAvailable(10, 19) != 10) {
            throw new AssertionError("Expected 10 seats free in range [10, 19]");
        }
        if (!tree.isRangeFree(10, 19)) {
            throw new AssertionError("Range [10, 19] should be completely free");
        }
    }

    public static void testAtomicLockAndConflict() {
        SegmentTree tree = new SegmentTree(100);

        // Lock seats 15 to 20 (6 seats)
        boolean locked = tree.lockRange(15, 20);
        if (!locked) {
            throw new AssertionError("Expected initial lock on [15, 20] to succeed");
        }
        if (tree.queryAvailable(15, 20) != 0) {
            throw new AssertionError("Expected 0 seats free in [15, 20] after lock");
        }
        if (tree.getTotalAvailable() != 94) {
            throw new AssertionError("Expected 94 seats total available after 6 locked");
        }

        // Try overlapping lock [18, 25] -> Should FAIL because 18, 19, 20 are locked
        boolean overlapping = tree.lockRange(18, 25);
        if (overlapping) {
            throw new AssertionError("Overlapping lock [18, 25] MUST FAIL when [15, 20] is locked");
        }

        // Verify partial seats [21, 25] were NOT erroneously consumed
        if (tree.queryAvailable(21, 25) != 5) {
            throw new AssertionError("Seats [21, 25] must remain available after failed partial lock");
        }
    }

    public static void testReleaseAndAvailabilityRestoration() {
        SegmentTree tree = new SegmentTree(50);
        tree.lockRange(5, 10);
        if (tree.getTotalAvailable() != 44) {
            throw new AssertionError("Expected 44 seats available after locking 6");
        }

        tree.releaseRange(5, 10);
        if (tree.getTotalAvailable() != 50) {
            throw new AssertionError("Expected all 50 seats available after release");
        }
        if (!tree.isRangeFree(5, 10)) {
            throw new AssertionError("Seats [5, 10] should be completely free after release");
        }
    }

    public static void testConcurrentSeatLockRace() {
        final int totalSeats = 100;
        SegmentTree tree = new SegmentTree(totalSeats);

        // 10 concurrent threads all fighting to book the EXACT SAME range [40, 44]
        final int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threads);
        AtomicInteger successLocks = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (tree.lockRange(40, 44)) {
                        successLocks.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        try {
            endLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
        }

        // Exactly ONE thread must win! Zero double-booking guarantee
        if (successLocks.get() != 1) {
            throw new AssertionError("RACE CONDITION DETECTED! Expected exactly 1 winner, but " 
                + successLocks.get() + " threads acquired the same seat range!");
        }
    }

    public static void main(String[] args) {
        runAllTests();
    }
}
