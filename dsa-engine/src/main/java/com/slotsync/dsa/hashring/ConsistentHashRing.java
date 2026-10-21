package com.slotsync.dsa.hashring;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * High-performance Consistent Hashing Ring with Virtual Nodes.
 * 
 * Uniformly distributes user traffic and event partitions across worker servers,
 * preventing hot-spots during 100,000-request bursts with zero external dependencies.
 */
public class ConsistentHashRing<T> {

    private final int numberOfReplicas;
    private final ConcurrentSkipListMap<Long, T> ring = new ConcurrentSkipListMap<>();
    private final Set<T> physicalNodes = new HashSet<>();
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();

    public ConsistentHashRing(int numberOfReplicas) {
        if (numberOfReplicas <= 0) {
            throw new IllegalArgumentException("Replicas must be positive: " + numberOfReplicas);
        }
        this.numberOfReplicas = numberOfReplicas;
    }

    public ConsistentHashRing(int numberOfReplicas, Collection<T> initialNodes) {
        this(numberOfReplicas);
        if (initialNodes != null) {
            for (T node : initialNodes) {
                addNode(node);
            }
        }
    }

    /**
     * Adds a physical node to the ring with its virtual replica nodes.
     */
    public void addNode(T node) {
        rwl.writeLock().lock();
        try {
            if (physicalNodes.add(node)) {
                for (int i = 0; i < numberOfReplicas; i++) {
                    long hash = hash(node.toString() + "#vnode#" + i);
                    ring.put(hash, node);
                }
            }
        } finally {
            rwl.writeLock().unlock();
        }
    }

    /**
     * Removes a physical node and all its virtual replicas from the ring.
     */
    public void removeNode(T node) {
        rwl.writeLock().lock();
        try {
            if (physicalNodes.remove(node)) {
                for (int i = 0; i < numberOfReplicas; i++) {
                    long hash = hash(node.toString() + "#vnode#" + i);
                    ring.remove(hash);
                }
            }
        } finally {
            rwl.writeLock().unlock();
        }
    }

    /**
     * Routes a user/request key to the assigned physical node.
     * Time Complexity: O(log K) where K is the total number of virtual nodes.
     */
    public T getNode(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        rwl.readLock().lock();
        try {
            if (ring.isEmpty()) {
                return null;
            }
            long hash = hash(key);
            Map.Entry<Long, T> entry = ring.ceilingEntry(hash);
            if (entry == null) {
                // Wrap around to the beginning of the circle
                entry = ring.firstEntry();
            }
            return entry.getValue();
        } finally {
            rwl.readLock().unlock();
        }
    }

    public Set<T> getPhysicalNodes() {
        rwl.readLock().lock();
        try {
            return Collections.unmodifiableSet(new HashSet<>(physicalNodes));
        } finally {
            rwl.readLock().unlock();
        }
    }

    public int getTotalVirtualNodes() {
        return ring.size();
    }

    /**
     * Standard 64-bit hash derived from MD5 digest.
     */
    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            // Extract 8 bytes to construct a 64-bit integer
            long h = 0;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (digest[i] & 0xFF);
            }
            return h;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 digest algorithm unavailable", e);
        }
    }
}
