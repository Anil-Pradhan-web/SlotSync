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

    private final java.util.Map<String, LockNode> activeReservations = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<int[]> confirmedBookings = new java.util.concurrent.CopyOnWriteArrayList<>();

    public record LockResult(boolean success, String lockId, int startSeat, int endSeat, long expiresAt, String message) {}

    public SlotLockEngine(int totalSeats, Consumer<LockNode> onExpiredListener) {
        this.segmentTree = new SegmentTree(totalSeats);
        this.ttlReaper = new MinHeapTTLReaper(expiredNode -> {
            // Auto-reclaim seats into Segment Tree when payment expires!
            activeReservations.remove(expiredNode.getLockId());
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

    public java.util.List<int[]> findAvailableBatches(int targetSeats, int maxBatchSize) {
        return segmentTree.findNextAvailableBatches(targetSeats, maxBatchSize);
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
        activeReservations.put(lockId, node);
        ttlReaper.registerLock(node);

        return new LockResult(true, lockId, startSeat, endSeat, node.getExpiryTimestamp(), "LOCKED_AWAITING_PAYMENT");
    }

    /**
     * Confirms booking upon payment success, cancelling the auto-expiry timer
     * and recording the seat range into confirmed bookings.
     */
    public boolean confirmReservation(String lockId) {
        LockNode node = activeReservations.remove(lockId);
        if (node != null) {
            confirmedBookings.add(new int[]{node.getStartSeat(), node.getEndSeat()});
        }
        return ttlReaper.confirmLock(lockId);
    }

    /**
     * Voluntarily cancels hold, immediately releasing seats back into pool.
     */
    public boolean releaseReservation(String lockId) {
        activeReservations.remove(lockId);
        return ttlReaper.cancelLock(lockId);
    }

    public static final int[][] DEFAULT_SEEDS = new int[][] {
        // Coach S1 (5 seats non-continuous)
        {4, 4}, {11, 11}, {23, 23}, {40, 40}, {58, 58},
        // Coach S2 (6 seats non-continuous)
        {76, 76}, {85, 85}, {99, 99}, {112, 112}, {125, 125}, {138, 138},
        // Coach S3 (6 seats non-continuous)
        {148, 148}, {161, 161}, {175, 175}, {189, 189}, {203, 203}, {212, 212},
        // Coach S4 (16 seats)
        {220, 221}, {228, 229}, {235, 235}, {242, 243}, {250, 251}, {258, 259}, {266, 267}, {274, 275}, {281, 281},
        // Coach S5 (17 seats)
        {292, 293}, {300, 301}, {308, 309}, {315, 316}, {322, 323}, {330, 331}, {338, 339}, {346, 347}, {354, 354},
        // Coach S6 (16 seats)
        {364, 365}, {372, 373}, {380, 381}, {388, 389}, {396, 397}, {404, 405}, {412, 413}, {420, 421},
        // Coach S7 (17 seats)
        {436, 437}, {444, 445}, {452, 453}, {460, 461}, {468, 469}, {476, 477}, {484, 485}, {492, 493}, {500, 500},
        // Coach S8 (16 seats)
        {508, 509}, {516, 517}, {524, 525}, {532, 533}, {540, 541}, {548, 549}, {556, 557}, {564, 565},
        // Coach S9 (17 seats)
        {580, 581}, {588, 589}, {596, 597}, {604, 605}, {612, 613}, {620, 621}, {628, 629}, {636, 637}, {644, 644},
        // Coach S10 (17 seats)
        {652, 653}, {660, 661}, {668, 669}, {676, 677}, {684, 685}, {692, 693}, {700, 701}, {708, 709}, {716, 716},
        // Coach S11 (17 seats)
        {724, 725}, {732, 733}, {740, 741}, {748, 749}, {756, 757}, {764, 765}, {772, 773}, {780, 781}, {788, 788},
        // Coach S12 (17 seats)
        {796, 797}, {804, 805}, {812, 813}, {820, 821}, {828, 829}, {836, 837}, {844, 845}, {852, 853}, {860, 860},
        // Coach S13 (17 seats)
        {868, 869}, {876, 877}, {884, 885}, {892, 893}, {900, 901}, {908, 909}, {916, 917}, {924, 925}, {932, 932},
        // Coach S14 (16 seats)
        {940, 941}, {948, 949}, {956, 957}, {964, 965}, {972, 973}, {980, 981}, {988, 989}, {996, 997}
    };

    private final String trainNumber = "18463";
    private final String trainName = "Prashanti Express";
    private final String sourceStation = "Bhubaneswar (BBS)";
    private final String destinationStation = "KSR Bengaluru City Jn (SBC)";
    private final String route = "BBS ➔ SBC";
    private final double basePrice = 1450.00;

    public String getTrainNumber() { return trainNumber; }
    public String getTrainName() { return trainName; }
    public String getSourceStation() { return sourceStation; }
    public String getDestinationStation() { return destinationStation; }
    public String getRoute() { return route; }
    public double getBasePrice() { return basePrice; }

    public void seedBookedRange(int startSeat, int endSeat) {
        segmentTree.lockRange(startSeat, endSeat);
        confirmedBookings.add(new int[]{startSeat, endSeat});
    }

    public void seedInitialBookings() {
        for (int[] range : DEFAULT_SEEDS) {
            seedBookedRange(range[0], range[1]);
        }
    }

    public java.util.List<int[]> getConfirmedBookedRanges() {
        return confirmedBookings;
    }

    public void resetAll() {
        for (String lockId : new java.util.ArrayList<>(activeReservations.keySet())) {
            releaseReservation(lockId);
        }
        activeReservations.clear();
        confirmedBookings.clear();
        segmentTree.releaseRange(0, segmentTree.getTotalSeats() - 1);
        seedInitialBookings();
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

    public java.util.Collection<LockNode> getActiveLocks() {
        return ttlReaper.getActiveLocks();
    }

    @Override
    public void close() {
        ttlReaper.close();
    }
}
