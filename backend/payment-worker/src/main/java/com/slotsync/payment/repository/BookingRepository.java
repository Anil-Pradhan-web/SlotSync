package com.slotsync.payment.repository;

import com.slotsync.payment.model.Booking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe transactional persistence repository for confirmed bookings.
 * Combines ultra-fast in-memory cache with durable append-only disk storage (WAL).
 */
public class BookingRepository {

    private final Map<String, Booking> byOrderId = new ConcurrentHashMap<>();
    private final Map<String, Booking> byLockId = new ConcurrentHashMap<>();
    private final File storageFile;

    public BookingRepository() {
        this((File) null);
    }

    public BookingRepository(boolean persist) {
        this(persist ? resolveDefaultStorageFile() : null);
    }

    public BookingRepository(File storageFile) {
        this.storageFile = storageFile;
        loadFromDisk();
    }

    public static BookingRepository persistent() {
        return new BookingRepository(true);
    }

    private static File resolveDefaultStorageFile() {
        // Resolve backend/database/bookings_wal.log
        File dbDir = new File("database");
        if (!dbDir.exists()) {
            File backendDb = new File("backend/database");
            if (backendDb.exists()) {
                dbDir = backendDb;
            } else {
                dbDir.mkdirs();
            }
        }
        return new File(dbDir, "bookings_wal.log");
    }

    public synchronized Booking save(Booking booking) {
        byOrderId.put(booking.getOrderId(), booking);
        byLockId.put(booking.getLockId(), booking);
        persistToDisk(booking);
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

    public synchronized void clear() {
        byOrderId.clear();
        byLockId.clear();
        if (storageFile != null && storageFile.exists()) {
            storageFile.delete();
        }
    }

    private void persistToDisk(Booking booking) {
        if (storageFile == null) return;
        try {
            File parent = storageFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(storageFile, StandardCharsets.UTF_8, true))) {
                // Format: bookingId|orderId|lockId|userId|startSeat|endSeat|amountPaid|transactionRef|status|bookedAt
                String line = String.format("%s|%s|%s|%s|%d|%d|%.2f|%s|%s|%d%n",
                        booking.getBookingId(),
                        booking.getOrderId(),
                        booking.getLockId(),
                        booking.getUserId(),
                        booking.getStartSeat(),
                        booking.getEndSeat(),
                        booking.getAmountPaid(),
                        booking.getTransactionRef(),
                        booking.getStatus(),
                        booking.getBookedAt());
                writer.write(line);
                writer.flush();
            }
        } catch (IOException e) {
            System.err.println("[WARN] Failed to write booking to disk WAL: " + e.getMessage());
        }
    }

    private synchronized void loadFromDisk() {
        if (storageFile == null || !storageFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(storageFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|");
                if (parts.length >= 8) {
                    String bookingId = parts[0];
                    String orderId = parts[1];
                    String lockId = parts[2];
                    String userId = parts[3];
                    int startSeat = Integer.parseInt(parts[4]);
                    int endSeat = Integer.parseInt(parts[5]);
                    double amountPaid = Double.parseDouble(parts[6]);
                    String txnRef = parts[7];
                    Booking booking = new Booking(bookingId, orderId, lockId, userId, startSeat, endSeat, amountPaid, txnRef);
                    if (parts.length >= 9) {
                        booking.setStatus(parts[8]);
                    }
                    byOrderId.put(orderId, booking);
                    byLockId.put(lockId, booking);
                }
            }
        } catch (Exception e) {
            System.err.println("[WARN] Failed to load bookings from disk: " + e.getMessage());
        }
    }
}
