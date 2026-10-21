package com.slotsync.lock.engine;

import com.slotsync.dsa.segmenttree.SegmentTree;
import com.slotsync.dsa.ttl.LockNode;
import com.slotsync.dsa.ttl.MinHeapTTLReaper;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Core Slot & Seat Reservation Engine (Worker 3: The Seat Counter).
 * 
 * Combines in-memory SegmentTree (for O(log N) contiguous seat range checks)
 * with MinHeapTTLReaper (for automatic 5-minute ghost inventory release).
 */
public class SlotLockEngine implements AutoCloseable {

    private final SegmentTree segmentTree;
    private final MinHeapTTLReaper ttlReaper;
    private final AtomicInteger expiredAutoReleaseCount = new AtomicInteger(0);

    public record LockResult(boolean success, String lockId, int startSeat, int endSeat, long expiresAt, String message) {}

    public SlotLockEngine(int totalSeats, Consumer<LockNode> onExpiredListener) {
        this.segmentTree = new SegmentTree(totalSeats);
        this.ttlReaper = new MinHeapTTLReaper(expiredNode -> {
            // Auto-reclaim seats into Segment Tree when payment expires!
            segmentTree.releaseRange(expiredNode.getStartSeat(), expiredNode.getEndSeat());
            expiredAutoReleaseCount.incrementAndGet();
            if (onExpiredListener != null) {
                onExpiredListener.accept(expiredNode);
            }
        });
    }

    public SlotLockEngine(int totalSeats) {
        this(totalSeats, null);
    }

    /**
     * Checks how many contiguous seats are available in the specified range.
     */
    public int checkAvailability(int startSeat, int endSeat) {
        return segmentTree.queryAvailable(startSeat, endSeat);
    }

    public boolean isRangeFree(int startSeat, int endSeat) {
        return segmentTree.isRangeFree(startSeat, endSeat);
    }

    /**
     * Atomically locks seats and schedules 5-minute auto-expiry hold.
     * All-or-nothing guarantee: zero partial locks!
     */
    public LockResult reserveSlots(String userId, int startSeat, int endSeat, long ttlMillis) {
        boolean locked = segmentTree.lockRange(startSeat, endSeat);
        if (!locked) {
            return new LockResult(false, null, startSeat, endSeat, 0, "SLOTS_UNAVAILABLE");
        }

        String lockId = "lck_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        LockNode node = new LockNode(lockId, userId, startSeat, endSeat, ttlMillis);
        ttlReaper.registerLock(node);

        return new LockResult(true, lockId, startSeat, endSeat, node.getExpiryTimestamp(), "LOCKED_AWAITING_PAYMENT");
    }

    /**
     * Confirms booking upon payment success, cancelling the auto-expiry timer.
     */
    public boolean confirmReservation(String lockId) {
        return ttlReaper.confirmLock(lockId);
    }

    /**
     * Voluntarily cancels hold, immediately releasing seats back into pool.
     */
    public boolean releaseReservation(String lockId) {
        return ttlReaper.cancelLock(lockId);
    }

    public int getTotalAvailableSeats() {
        return segmentTree.getTotalAvailable();
    }

    public int getTotalSeats() {
        return segmentTree.getTotalSeats();
    }

    public int getAutoReapedCount() {
        return expiredAutoReleaseCount.get();
    }

    @Override
    public void close() {
        ttlReaper.close();
    }
}
