package com.slotsync.waitingroom.service;

import com.slotsync.waitingroom.model.QueueTicket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-concurrency In-Memory Virtual Waiting Room Service.
 * 
 * Safely absorbs 100,000+ incoming users, assigns deterministic queue numbers,
 * and feeds controlled batches of users downstream without overloading backend databases.
 */
public class QueueService {

    private final ConcurrentLinkedQueue<QueueTicket> waitingQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, QueueTicket> ticketIndex = new ConcurrentHashMap<>();
    private final AtomicInteger admittedCounter = new AtomicInteger(0);

    /**
     * Places a user into the waiting line.
     */
    public QueueTicket enqueue(String userId, long eventId) {
        String ticketId = "tkt_" + UUID.randomUUID().toString().substring(0, 10);
        QueueTicket ticket = new QueueTicket(ticketId, userId, eventId);

        ticketIndex.put(ticketId, ticket);
        waitingQueue.offer(ticket);
        return ticket;
    }

    /**
     * Looks up an existing ticket.
     */
    public QueueTicket getTicket(String ticketId) {
        return ticketIndex.get(ticketId);
    }

    /**
     * Calculates the user's current 1-based position in the waiting line.
     * @return 1-based position, or 0 if already admitted/not found.
     */
    public int getQueuePosition(String ticketId) {
        QueueTicket target = ticketIndex.get(ticketId);
        if (target == null || target.getStatus() != QueueTicket.Status.WAITING) {
            return 0;
        }

        int pos = 1;
        for (QueueTicket item : waitingQueue) {
            if (item.getTicketId().equals(ticketId)) {
                return pos;
            }
            if (item.getStatus() == QueueTicket.Status.WAITING) {
                pos++;
            }
        }
        return 0;
    }

    /**
     * Admits a batch of up to `batchSize` waiting users into the booking room.
     */
    public List<QueueTicket> admitBatch(int batchSize, long tokenTtlMillis) {
        List<QueueTicket> admitted = new ArrayList<>(batchSize);
        while (admitted.size() < batchSize) {
            QueueTicket candidate = waitingQueue.poll();
            if (candidate == null) {
                break; // Queue is empty
            }

            if (candidate.getStatus() == QueueTicket.Status.WAITING) {
                String token = "adm_" + UUID.randomUUID().toString().replace("-", "");
                candidate.admit(token, tokenTtlMillis);
                admitted.add(candidate);
                admittedCounter.incrementAndGet();
            }
        }
        return admitted;
    }

    public int getWaitingCount() {
        return waitingQueue.size();
    }

    public int getTotalAdmittedCount() {
        return admittedCounter.get();
    }

    public void clear() {
        waitingQueue.clear();
        ticketIndex.clear();
        admittedCounter.set(0);
    }
}
