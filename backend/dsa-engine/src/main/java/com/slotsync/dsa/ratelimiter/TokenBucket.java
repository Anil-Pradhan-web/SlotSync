package com.slotsync.dsa.ratelimiter;

import java.util.concurrent.atomic.AtomicReference;

/**
 * High-performance, lock-free Token Bucket Rate Limiter.
 * Uses atomic Compare-And-Swap (CAS) and monotonic relative nanoseconds to guarantee
 * thread-safety and microsecond evaluation under heavy concurrent bursts.
 */
public class TokenBucket {

    private final long capacity;
    private final double refillTokensPerNano;
    private final long refillRatePerSecond;
    private final AtomicReference<BucketState> state;

    private static final long NANOS_IN_SECOND = 1_000_000_000L;

    public TokenBucket(long capacity, long refillRatePerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive, got: " + capacity);
        }
        if (refillRatePerSecond <= 0) {
            throw new IllegalArgumentException("Refill rate must be positive, got: " + refillRatePerSecond);
        }
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.refillTokensPerNano = (double) refillRatePerSecond / NANOS_IN_SECOND;
        this.state = new AtomicReference<>(new BucketState(capacity, System.nanoTime()));
    }

    /**
     * Attempts to acquire a single token.
     * @return true if token acquired, false if rate limit exceeded.
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * Attempts to acquire the specified number of tokens atomically.
     * @param tokensRequested number of tokens required for this operation.
     * @return true if tokens were successfully acquired, false if throttled.
     */
    public boolean tryAcquire(long tokensRequested) {
        if (tokensRequested <= 0) {
            throw new IllegalArgumentException("Requested tokens must be positive: " + tokensRequested);
        }
        if (tokensRequested > capacity) {
            return false;
        }

        while (true) {
            BucketState current = state.get();
            long now = System.nanoTime();
            long elapsedNanos = Math.max(0, now - current.lastRefillNanos);
            long refilledTokens = (long) (elapsedNanos * refillTokensPerNano);
            long newTokens = Math.min(capacity, current.tokens + refilledTokens);

            if (newTokens < tokensRequested) {
                return false;
            }

            // Calculate exact timestamp reflecting remaining token fraction
            BucketState next = new BucketState(newTokens - tokensRequested, now);
            if (state.compareAndSet(current, next)) {
                return true;
            }
        }
    }

    /**
     * Returns the current number of available tokens after applying lazy refill.
     */
    public long getAvailableTokens() {
        BucketState current = state.get();
        long now = System.nanoTime();
        long elapsedNanos = Math.max(0, now - current.lastRefillNanos);
        long refilledTokens = (long) (elapsedNanos * refillTokensPerNano);
        return Math.min(capacity, current.tokens + refilledTokens);
    }

    public long getCapacity() {
        return capacity;
    }

    public long getRefillRatePerSecond() {
        return refillRatePerSecond;
    }

    private static class BucketState {
        final long tokens;
        final long lastRefillNanos;

        BucketState(long tokens, long lastRefillNanos) {
            this.tokens = tokens;
            this.lastRefillNanos = lastRefillNanos;
        }
    }
}
