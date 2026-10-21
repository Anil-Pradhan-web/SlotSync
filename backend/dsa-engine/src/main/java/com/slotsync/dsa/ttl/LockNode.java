package com.slotsync.dsa.ttl;

import java.util.Objects;

/**
 * Represents a temporary seat reservation hold awaiting payment completion.
 */
public class LockNode implements Comparable<LockNode> {

    private final String lockId;
    private final String userId;
    private final int startSeat;
    private final int endSeat;
    private final long expiryTimestamp;
    private volatile boolean confirmed;

    public LockNode(String lockId, String userId, int startSeat, int endSeat, long ttlMillis) {
        this.lockId = Objects.requireNonNull(lockId, "lockId cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.startSeat = startSeat;
        this.endSeat = endSeat;
        this.expiryTimestamp = System.currentTimeMillis() + ttlMillis;
        this.confirmed = false;
    }

    public String getLockId() {
        return lockId;
    }

    public String getUserId() {
        return userId;
    }

    public int getStartSeat() {
        return startSeat;
    }

    public int getEndSeat() {
        return endSeat;
    }

    public long getExpiryTimestamp() {
        return expiryTimestamp;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void markConfirmed() {
        this.confirmed = true;
    }

    @Override
    public int compareTo(LockNode other) {
        return Long.compare(this.expiryTimestamp, other.expiryTimestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LockNode lockNode)) return false;
        return Objects.equals(lockId, lockNode.lockId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lockId);
    }
}
