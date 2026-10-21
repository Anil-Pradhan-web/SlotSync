package com.slotsync.dsa.segmenttree;

import java.util.concurrent.locks.StampedLock;

/**
 * Segment Tree for ultra-fast, in-memory seat and slot range reservations.
 * 
 * Supports:
 * - Query available seats in range [L, R] in O(log N) time.
 * - Atomic range reservation [L, R] in O((R - L + 1) * log N) time with zero partial locks.
 * - Atomic range release [L, R] on payment cancellation or TTL expiry.
 * - StampedLock concurrency for high-throughput reads and exclusive writes.
 */
public class SegmentTree {

    private final int totalSeats;
    private final int[] tree;
    private final boolean[] seatStatus; // true = available, false = locked/booked
    private final StampedLock lock = new StampedLock();

    public SegmentTree(int totalSeats) {
        if (totalSeats <= 0) {
            throw new IllegalArgumentException("Total seats must be positive: " + totalSeats);
        }
        this.totalSeats = totalSeats;
        this.tree = new int[4 * totalSeats];
        this.seatStatus = new boolean[totalSeats];
        build(1, 0, totalSeats - 1);
    }

    private void build(int node, int start, int end) {
        if (start == end) {
            tree[node] = 1; // Initially, all seats are available
            seatStatus[start] = true;
            return;
        }
        int mid = start + (end - start) / 2;
        int leftNode = 2 * node;
        int rightNode = 2 * node + 1;

        build(leftNode, start, mid);
        build(rightNode, mid + 1, end);
        tree[node] = tree[leftNode] + tree[rightNode];
    }

    /**
     * Queries how many seats are available in the range [left, right] (0-indexed).
     * Time Complexity: O(log N)
     */
    public int queryAvailable(int left, int right) {
        validateRange(left, right);
        long stamp = lock.tryOptimisticRead();
        int result = queryInternal(1, 0, totalSeats - 1, left, right);
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                result = queryInternal(1, 0, totalSeats - 1, left, right);
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return result;
    }

    private int queryInternal(int node, int start, int end, int left, int right) {
        if (right < start || end < left) {
            return 0; // Completely outside query range
        }
        if (left <= start && end <= right) {
            return tree[node]; // Completely inside query range
        }
        int mid = start + (end - start) / 2;
        int leftSum = queryInternal(2 * node, start, mid, left, right);
        int rightSum = queryInternal(2 * node + 1, mid + 1, end, left, right);
        return leftSum + rightSum;
    }

    /**
     * Checks if all contiguous seats in [left, right] are free.
     */
    public boolean isRangeFree(int left, int right) {
        int expected = right - left + 1;
        return queryAvailable(left, right) == expected;
    }

    /**
     * Atomically locks all seats in range [left, right].
     * If ANY seat in the range is already locked, the operation fails and no seats are modified.
     * Time Complexity: O(K log N) where K = right - left + 1.
     * @return true if all seats successfully locked, false if any seat was unavailable.
     */
    public boolean lockRange(int left, int right) {
        validateRange(left, right);
        long stamp = lock.writeLock();
        try {
            // Step 1: Verify all seats in range are currently available
            int available = queryInternal(1, 0, totalSeats - 1, left, right);
            int expected = right - left + 1;
            if (available != expected) {
                return false; // Conflict! At least one seat is already locked
            }

            // Step 2: Atomically mark seats as occupied
            for (int i = left; i <= right; i++) {
                updateSeatInternal(1, 0, totalSeats - 1, i, 0);
                seatStatus[i] = false;
            }
            return true;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Atomically releases seats in range [left, right] back to the available pool.
     */
    public void releaseRange(int left, int right) {
        validateRange(left, right);
        long stamp = lock.writeLock();
        try {
            for (int i = left; i <= right; i++) {
                if (!seatStatus[i]) {
                    updateSeatInternal(1, 0, totalSeats - 1, i, 1);
                    seatStatus[i] = true;
                }
            }
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    private void updateSeatInternal(int node, int start, int end, int targetIdx, int val) {
        if (start == end) {
            tree[node] = val;
            return;
        }
        int mid = start + (end - start) / 2;
        if (targetIdx <= mid) {
            updateSeatInternal(2 * node, start, mid, targetIdx, val);
        } else {
            updateSeatInternal(2 * node + 1, mid + 1, end, targetIdx, val);
        }
        tree[node] = tree[2 * node] + tree[2 * node + 1];
    }

    public int getTotalAvailable() {
        long stamp = lock.tryOptimisticRead();
        int total = tree[1];
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                total = tree[1];
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return total;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    private void validateRange(int left, int right) {
        if (left < 0 || right >= totalSeats || left > right) {
            throw new IndexOutOfBoundsException(
                String.format("Invalid seat range [%d, %d] for totalSeats=%d", left, right, totalSeats));
        }
    }
}
