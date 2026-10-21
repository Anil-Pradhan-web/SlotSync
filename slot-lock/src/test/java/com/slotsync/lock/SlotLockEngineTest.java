package com.slotsync.lock;

import com.slotsync.lock.engine.SlotLockEngine;

/**
 * Unit & Concurrency Tests for SlotLockEngine.
 */
public class SlotLockEngineTest {

    public static void runAllTests() {
        System.out.println("--> Running SlotLockEngineTest...");
        testAtomicLockAndConflict();
        testGhostInventoryAutoReclamation();
        testVoluntaryUnlock();
        System.out.println("  [PASS] All SlotLockEngine tests passed successfully!\n");
    }

    public static void testAtomicLockAndConflict() {
        try (SlotLockEngine engine = new SlotLockEngine(100)) {
            if (engine.getTotalAvailableSeats() != 100) {
                throw new AssertionError("Expected 100 seats available");
            }

            // Reserve seats 10 to 14 (5 seats)
            SlotLockEngine.LockResult r1 = engine.reserveSlots("user-1", 10, 14, 300_000L);
            if (!r1.success()) {
                throw new AssertionError("Expected initial seat lock to succeed");
            }
            if (engine.getTotalAvailableSeats() != 95) {
                throw new AssertionError("Expected 95 seats available after locking 5");
            }

            // Try overlapping lock 12 to 18 -> Must FAIL
            SlotLockEngine.LockResult r2 = engine.reserveSlots("user-2", 12, 18, 300_000L);
            if (r2.success()) {
                throw new AssertionError("Overlapping lock MUST FAIL!");
            }
            if (engine.getTotalAvailableSeats() != 95) {
                throw new AssertionError("Total seats must remain 95 after failed partial lock");
            }
        }
    }

    public static void testGhostInventoryAutoReclamation() {
        try (SlotLockEngine engine = new SlotLockEngine(50)) {
            // Reserve seats with short 150ms TTL
            SlotLockEngine.LockResult r = engine.reserveSlots("abandoned-user", 20, 24, 150L);
            if (!r.success()) {
                throw new AssertionError("Initial lock failed");
            }
            if (engine.getTotalAvailableSeats() != 45) {
                throw new AssertionError("Expected 45 seats after lock");
            }

            // Sleep 350ms to let TTL expire
            try {
                Thread.sleep(350);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Verify seats were AUTOMATICALLY restored by MinHeapTTLReaper into SegmentTree!
            if (engine.getTotalAvailableSeats() != 50) {
                throw new AssertionError("Ghost inventory failure! Expected 50 seats after auto-reclaim, got: " 
                    + engine.getTotalAvailableSeats());
            }
            if (!engine.isRangeFree(20, 24)) {
                throw new AssertionError("Range [20, 24] should be free again after auto-expiry");
            }
        }
    }

    public static void testVoluntaryUnlock() {
        try (SlotLockEngine engine = new SlotLockEngine(50)) {
            SlotLockEngine.LockResult r = engine.reserveSlots("buyer-c", 30, 34, 300_000L);
            if (engine.getTotalAvailableSeats() != 45) {
                throw new AssertionError("Expected 45 seats");
            }

            boolean released = engine.releaseReservation(r.lockId());
            if (!released) {
                throw new AssertionError("Failed to release reservation");
            }
            if (engine.getTotalAvailableSeats() != 50) {
                throw new AssertionError("Expected all 50 seats restored after voluntary unlock");
            }
        }
    }

    public static void main(String[] args) {
        runAllTests();
    }
}
