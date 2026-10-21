package com.slotsync.waitingroom.dispatcher;

import com.slotsync.waitingroom.model.QueueTicket;
import com.slotsync.waitingroom.service.QueueService;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Background Dispatcher that steadily feeds waiting users downstream
 * in regulated batches (e.g. 200 users every second).
 */
public class BatchAdmissionDispatcher implements AutoCloseable {

    private final QueueService queueService;
    private final int batchSize;
    private final long tokenTtlMillis;
    private final ScheduledExecutorService scheduler;
    private final Consumer<List<QueueTicket>> onBatchAdmitted;
    private volatile boolean running = false;

    public BatchAdmissionDispatcher(
            QueueService queueService, 
            int batchSize, 
            long tokenTtlMillis,
            Consumer<List<QueueTicket>> onBatchAdmitted) {
        this.queueService = queueService;
        this.batchSize = batchSize;
        this.tokenTtlMillis = tokenTtlMillis;
        this.onBatchAdmitted = onBatchAdmitted;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Batch-Admission-Dispatcher");
            t.setDaemon(true);
            return t;
        });
    }

    public BatchAdmissionDispatcher(QueueService queueService, int batchSize) {
        this(queueService, batchSize, 60_000L, null);
    }

    public synchronized void start(long intervalMillis) {
        if (!running) {
            running = true;
            scheduler.scheduleAtFixedRate(this::dispatchBatch, 0, intervalMillis, TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void start() {
        start(1000L); // Default 1 second
    }

    public List<QueueTicket> dispatchBatch() {
        if (queueService.getWaitingCount() == 0) {
            return List.of();
        }
        List<QueueTicket> admitted = queueService.admitBatch(batchSize, tokenTtlMillis);
        if (!admitted.isEmpty() && onBatchAdmitted != null) {
            onBatchAdmitted.accept(admitted);
        }
        return admitted;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        running = false;
        scheduler.shutdownNow();
    }
}
