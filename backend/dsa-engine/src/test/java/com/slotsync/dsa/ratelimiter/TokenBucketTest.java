package com.slotsync.dsa.ratelimiter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit & Concurrency Tests for TokenBucket.
 */
public class TokenBucketTest {

    public static void runAllTests() {
        System.out.println("--> Running TokenBucketTest...");
        testInitialCapacityAndBurst();
        testRefillOverTime();
        testMultiThreadedConcurrency();
        System.out.println("  [PASS] All TokenBucket tests passed successfully!\n");
    }

    public static void testInitialCapacityAndBurst() {
        TokenBucket bucket = new TokenBucket(5, 1);
        for (int i = 0; i < 5; i++) {
            if (!bucket.tryAcquire()) {
                throw new AssertionError("Expected token acquire to succeed for request " + (i + 1));
            }
        }
        if (bucket.tryAcquire()) {
            throw new AssertionError("Expected 6th request to be throttled when capacity is 5");
        }
    }

    public static void testRefillOverTime() {
        // Refill rate: 10 tokens per second (1 token per 100ms)
        TokenBucket bucket = new TokenBucket(2, 10);
        if (!bucket.tryAcquire(2)) {
            throw new AssertionError("Failed to acquire initial 2 tokens");
        }
        if (bucket.tryAcquire()) {
            throw new AssertionError("Should be empty immediately after consuming all tokens");
        }

        try {
            Thread.sleep(250); // wait 250ms, should refill ~2 tokens
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!bucket.tryAcquire()) {
            throw new AssertionError("Expected token to be refilled after 250ms wait");
        }
    }

    public static void testMultiThreadedConcurrency() {
        final int capacity = 20;
        final int totalThreads = 100;
        TokenBucket bucket = new TokenBucket(capacity, 1); // Slow refill (1/sec)

        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger throttledCount = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    if (bucket.tryAcquire()) {
                        successCount.incrementAndGet();
                    } else {
                        throttledCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown(); // Trigger all threads simultaneously
        try {
            endGate.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
        }

        if (successCount.get() != capacity) {
            throw new AssertionError("Strict capacity invariant violated! Expected exactly " 
                + capacity + " successful acquires, got: " + successCount.get());
        }
        if (throttledCount.get() != (totalThreads - capacity)) {
            throw new AssertionError("Throttled count mismatch! Expected " 
                + (totalThreads - capacity) + ", got: " + throttledCount.get());
        }
    }

    public static void main(String[] args) {
        runAllTests();
    }
}
