package com.slotsync.payment.model;

import java.util.Objects;

/**
 * Represents a permanently confirmed booking ticket in durable storage.
 */
public class Booking {

    private final String bookingId;
    private final String orderId;
    private final String lockId;
    private final String userId;
    private final int startSeat;
    private final int endSeat;
    private final double amountPaid;
    private final String transactionRef;
    private final long bookedAt;
    private volatile String status;

    public Booking(
            String bookingId, 
            String orderId, 
            String lockId, 
            String userId, 
            int startSeat, 
            int endSeat, 
            double amountPaid, 
            String transactionRef) {
        this.bookingId = Objects.requireNonNull(bookingId, "bookingId cannot be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
        this.lockId = Objects.requireNonNull(lockId, "lockId cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.startSeat = startSeat;
        this.endSeat = endSeat;
        this.amountPaid = amountPaid;
        this.transactionRef = Objects.requireNonNull(transactionRef, "transactionRef cannot be null");
        this.bookedAt = System.currentTimeMillis();
        this.status = "CONFIRMED";
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getOrderId() {
        return orderId;
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

    public double getAmountPaid() {
        return amountPaid;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public long getBookedAt() {
        return bookedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
