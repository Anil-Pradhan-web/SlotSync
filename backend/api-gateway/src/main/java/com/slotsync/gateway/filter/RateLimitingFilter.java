package com.slotsync.gateway.filter;

import com.slotsync.dsa.ratelimiter.TokenBucket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Edge Rate Limiting Filter for API Gateway.
 * 
 * Protects downstream microservices from bot floods, DDoS bursts, and automated scripts
 * by enforcing Token Bucket rate limits per client IP or authenticated User ID.
 */
public class RateLimitingFilter {

    private final long defaultCapacity;
    private final long defaultRefillRatePerSecond;
    private final Map<String, TokenBucket> clientBuckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(long defaultCapacity, long defaultRefillRatePerSecond) {
        if (defaultCapacity <= 0 || defaultRefillRatePerSecond <= 0) {
            throw new IllegalArgumentException("Capacity and refill rate must be positive");
        }
        this.defaultCapacity = defaultCapacity;
        this.defaultRefillRatePerSecond = defaultRefillRatePerSecond;
    }

    /**
     * Checks if the incoming request from the specified client is permitted.
     * @param clientIdentifier IP address or user ID.
     * @return RateLimitResult indicating whether allowed, remaining tokens, and retry-after delay.
     */
    public RateLimitResult evaluate(String clientIdentifier) {
        if (clientIdentifier == null || clientIdentifier.isBlank()) {
            clientIdentifier = "anonymous-client";
        }

        TokenBucket bucket = clientBuckets.computeIfAbsent(clientIdentifier, 
            k -> new TokenBucket(defaultCapacity, defaultRefillRatePerSecond));

        boolean allowed = bucket.tryAcquire();
        long remaining = bucket.getAvailableTokens();
        long retryAfterSeconds = allowed ? 0 : 1;

        return new RateLimitResult(allowed, remaining, defaultCapacity, retryAfterSeconds);
    }

    public int getActiveClientCount() {
        return clientBuckets.size();
    }

    public void clear() {
        clientBuckets.clear();
    }

    public record RateLimitResult(
        boolean allowed, 
        long remainingTokens, 
        long limitCapacity, 
        long retryAfterSeconds
    ) {}
}
