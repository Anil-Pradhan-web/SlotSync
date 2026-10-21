package com.slotsync.gateway.filter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Edge Idempotency Filter for API Gateway.
 * 
 * Prevents duplicate bookings and multiple credit card charges when impatient users
 * double-click buttons or retry requests due to network lag.
 */
public class IdempotencyFilter {

    public enum Status {
        IN_PROGRESS,
        COMPLETED
    }

    public record CachedResponse(int statusCode, String body, long timestamp) {}

    private final Map<String, Record> keyStore = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public IdempotencyFilter(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public IdempotencyFilter() {
        this(300_000L); // Default 5 minutes
    }

    /**
     * Checks if the idempotency key has already been seen or completed.
     */
    public CheckResult checkAndRegister(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new CheckResult(CheckStatus.PASSTHROUGH, null);
        }

        long now = System.currentTimeMillis();
        cleanExpired(now);

        Record existing = keyStore.putIfAbsent(idempotencyKey, new Record(Status.IN_PROGRESS, null, now));
        if (existing == null) {
            return new CheckResult(CheckStatus.NEW_REQUEST, null);
        }

        if (existing.status == Status.COMPLETED && existing.cachedResponse != null) {
            return new CheckResult(CheckStatus.ALREADY_COMPLETED, existing.cachedResponse);
        }

        return new CheckResult(CheckStatus.IN_PROGRESS, null);
    }

    /**
     * Records the final response against the idempotency key for replay.
     */
    public void recordCompletion(String idempotencyKey, int statusCode, String responseBody) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            CachedResponse cached = new CachedResponse(statusCode, responseBody, System.currentTimeMillis());
            keyStore.put(idempotencyKey, new Record(Status.COMPLETED, cached, System.currentTimeMillis()));
        }
    }

    private void cleanExpired(long now) {
        keyStore.entrySet().removeIf(entry -> (now - entry.getValue().createdAt) > ttlMillis);
    }

    public int getTrackedKeysCount() {
        return keyStore.size();
    }

    public void clear() {
        keyStore.clear();
    }

    public enum CheckStatus {
        NEW_REQUEST,
        IN_PROGRESS,
        ALREADY_COMPLETED,
        PASSTHROUGH
    }

    public record CheckResult(CheckStatus status, CachedResponse cachedResponse) {}

    private static class Record {
        final Status status;
        final CachedResponse cachedResponse;
        final long createdAt;

        Record(Status status, CachedResponse cachedResponse, long createdAt) {
            this.status = status;
            this.cachedResponse = cachedResponse;
            this.createdAt = createdAt;
        }
    }
}
