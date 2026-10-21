package com.slotsync.dsa.ttl;

import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * In-memory TTL Reaper Engine based on a Min-Heap (PriorityQueue).
 * 
 * Solves the "Ghost Inventory" disaster:
 * When users abandon checkout, this engine detects and releases their locks
 * the exact millisecond their TTL expires, with ZERO database polling queries!
 */
public class MinHeapTTLReaper implements AutoCloseable {

    private final PriorityQueue<LockNode> minHeap = new PriorityQueue<>();
    private final Map<String, LockNode> activeLocks = new ConcurrentHashMap<>();
    private final Consumer<LockNode> onExpiryCallback;
    private final Object monitor = new Object();
    private final Thread reaperThread;
    private volatile boolean running = true;

    public MinHeapTTLReaper(Consumer<LockNode> onExpiryCallback) {
        this.onExpiryCallback = onExpiryCallback;
        this.reaperThread = new Thread(this::reaperLoop, "SlotSync-TTL-Reaper");
        this.reaperThread.setDaemon(true);
        this.reaperThread.start();
    }

    /**
     * Registers a new lock with the TTL engine.
     */
    public void registerLock(LockNode node) {
        synchronized (monitor) {
            activeLocks.put(node.getLockId(), node);
            minHeap.offer(node);
            monitor.notifyAll(); // Wake reaper thread if it was sleeping
        }
    }

    /**
     * Confirms payment for a lock, permanently preventing auto-release.
     * @return true if successfully confirmed, false if lock was already expired or not found.
     */
    public boolean confirmLock(String lockId) {
        LockNode node = activeLocks.remove(lockId);
        if (node != null && !node.isConfirmed()) {
            node.markConfirmed();
            return true;
        }
        return false;
    }

    /**
     * Explicitly cancels and frees a lock ahead of TTL expiration.
     */
    public boolean cancelLock(String lockId) {
        LockNode node = activeLocks.remove(lockId);
        if (node != null && !node.isConfirmed()) {
            node.markConfirmed(); // Prevent double release by reaper
            if (onExpiryCallback != null) {
                onExpiryCallback.accept(node);
            }
            return true;
        }
        return false;
    }

    public LockNode getLock(String lockId) {
        return activeLocks.get(lockId);
    }

    public int getActiveLockCount() {
        return activeLocks.size();
    }

    private void reaperLoop() {
        while (running) {
            try {
                LockNode expiredNode = null;
                synchronized (monitor) {
                    while (running && minHeap.isEmpty()) {
                        monitor.wait();
                    }
                    if (!running) break;

                    long now = System.currentTimeMillis();
                    LockNode earliest = minHeap.peek();

                    if (earliest != null) {
                        long delay = earliest.getExpiryTimestamp() - now;
                        if (delay <= 0) {
                            expiredNode = minHeap.poll();
                        } else {
                            monitor.wait(delay);
                        }
                    }
                }

                // Execute callback outside synchronized block to avoid holding monitor
                if (expiredNode != null) {
                    LockNode registered = activeLocks.remove(expiredNode.getLockId());
                    if (registered != null && !registered.isConfirmed()) {
                        if (onExpiryCallback != null) {
                            onExpiryCallback.accept(registered);
                        }
                    }
                }
            } catch (InterruptedException e) {
                if (!running) break;
            } catch (Exception e) {
                System.err.println("Unexpected error in TTL reaper loop: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        running = false;
        synchronized (monitor) {
            monitor.notifyAll();
        }
        reaperThread.interrupt();
    }
}
