package com.slotsync.payment.repository;

import com.slotsync.payment.model.Booking;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe transactional persistence repository for confirmed bookings.
 */
public class BookingRepository {

    private final Map<String, Booking> byOrderId = new ConcurrentHashMap<>();
    private final Map<String, Booking> byLockId = new ConcurrentHashMap<>();

    public synchronized Booking save(Booking booking) {
        byOrderId.put(booking.getOrderId(), booking);
        byLockId.put(booking.getLockId(), booking);
        return booking;
    }

    public Optional<Booking> findByOrderId(String orderId) {
        return Optional.ofNullable(byOrderId.get(orderId));
    }

    public Optional<Booking> findByLockId(String lockId) {
        return Optional.ofNullable(byLockId.get(lockId));
    }

    public Collection<Booking> findAll() {
        return Collections.unmodifiableCollection(byOrderId.values());
    }

    public int count() {
        return byOrderId.size();
    }

    public void clear() {
        byOrderId.clear();
        byLockId.clear();
    }
}
