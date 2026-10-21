package com.slotsync.dsa.ttl;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit & Integration Tests for MinHeapTTLReaper.
 */
public class MinHeapTTLReaperTest {

    public static void runAllTests() {
        System.out.println("--> Running MinHeapTTLReaperTest...");
        testAutoExpiryCallback();
        testConfirmationPreventsExpiry();
        testExplicitCancellation();
        System.out.println("  [PASS] All MinHeapTTLReaper tests passed successfully!\n");
    }

    public static void testAutoExpiryCallback() {
        CountDownLatch expiredLatch = new CountDownLatch(1);
        AtomicBoolean wasExpired = new AtomicBoolean(false);

        try (MinHeapTTLReaper reaper = new MinHeapTTLReaper(node -> {
            if ("lock-101".equals(node.getLockId())) {
                wasExpired.set(true);
                expiredLatch.countDown();
            }
        })) {
            // Register a lock with a short 150ms TTL
            LockNode node = new LockNode("lock-101", "user-A", 10, 12, 150);
            reaper.registerLock(node);

            boolean released = expiredLatch.await(500, TimeUnit.MILLISECONDS);
            if (!released || !wasExpired.get()) {
                throw new AssertionError("Expected lock-101 to auto-expire via reaper daemon!");
            }
            if (reaper.getActiveLockCount() != 0) {
                throw new AssertionError("Expected active lock count to be 0 after expiration");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void testConfirmationPreventsExpiry() {
        AtomicBoolean incorrectlyExpired = new AtomicBoolean(false);

        try (MinHeapTTLReaper reaper = new MinHeapTTLReaper(node -> {
            incorrectlyExpired.set(true);
        })) {
            // Register lock with 200ms TTL
            LockNode node = new LockNode("lock-202", "user-B", 20, 22, 200);
            reaper.registerLock(node);

            // User confirms payment after 50ms
            Thread.sleep(50);
            boolean confirmed = reaper.confirmLock("lock-202");
            if (!confirmed) {
                throw new AssertionError("Failed to confirm active lock-202");
            }

            // Wait 300ms (past TTL)
            Thread.sleep(300);

            if (incorrectlyExpired.get()) {
                throw new AssertionError("Confirmed lock MUST NOT be auto-expired by reaper!");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void testExplicitCancellation() {
        AtomicInteger releaseCallbackCount = new AtomicInteger(0);

        try (MinHeapTTLReaper reaper = new MinHeapTTLReaper(node -> {
            releaseCallbackCount.incrementAndGet();
        })) {
            LockNode node = new LockNode("lock-303", "user-C", 30, 31, 500);
            reaper.registerLock(node);

            // User voluntarily cancels lock
            boolean cancelled = reaper.cancelLock("lock-303");
            if (!cancelled) {
                throw new AssertionError("Failed to cancel active lock-303");
            }
            if (releaseCallbackCount.get() != 1) {
                throw new AssertionError("Expected cancellation to trigger immediate release callback");
            }

            // Wait for original TTL to pass; ensure reaper doesn't trigger callback again
            Thread.sleep(600);
            if (releaseCallbackCount.get() != 1) {
                throw new AssertionError("Reaper should not trigger duplicate release for cancelled lock!");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        runAllTests();
    }
}
