package com.slotsync.waitingroom.model;

import java.util.Objects;

/**
 * Represents a user's position and admission state in the virtual waiting line.
 */
public class QueueTicket {

    public enum Status {
        WAITING,
        ADMITTED,
        EXPIRED,
        COMPLETED
    }

    private final String ticketId;
    private final String userId;
    private final long eventId;
    private final long enqueuedAt;
    private volatile Status status;
    private volatile String admissionToken;
    private volatile long admissionExpiresAt;

    public QueueTicket(String ticketId, String userId, long eventId) {
        this.ticketId = Objects.requireNonNull(ticketId, "ticketId cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.eventId = eventId;
        this.enqueuedAt = System.currentTimeMillis();
        this.status = Status.WAITING;
    }

    public void admit(String token, long ttlMillis) {
        this.status = Status.ADMITTED;
        this.admissionToken = token;
        this.admissionExpiresAt = System.currentTimeMillis() + ttlMillis;
    }

    public boolean isAdmissionValid() {
        return status == Status.ADMITTED && System.currentTimeMillis() <= admissionExpiresAt;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getUserId() {
        return userId;
    }

    public long getEventId() {
        return eventId;
    }

    public long getEnqueuedAt() {
        return enqueuedAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getAdmissionToken() {
        return admissionToken;
    }

    public long getAdmissionExpiresAt() {
        return admissionExpiresAt;
    }
}
