package com.slotsync.payment;

import com.slotsync.payment.model.Booking;
import com.slotsync.payment.repository.BookingRepository;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * Verifies durability of confirmed tickets across JVM restarts (disk persistence).
 */
public class BookingPersistenceTest {

    public static void runAllTests() {
        System.out.println("--> Running BookingPersistenceTest...");
        testBookingSurvivesRestartOnDisk();
        System.out.println("  [PASS] All BookingPersistenceTest tests passed successfully!\n");
    }

    public static void testBookingSurvivesRestartOnDisk() {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("slotsync_test_wal_", ".log");
            tempFile.deleteOnExit();

            // 1. Session 1: Create repository connected to disk and save booking
            BookingRepository repoSession1 = new BookingRepository(tempFile);
            Booking booking = new Booking(
                "BKG-TEST-DISK-1",
                "ORD-DISK-001",
                "LCK-DISK-001",
                "USR-RAHUL",
                50,
                54,
                2500.00,
                "TXN_DISK_VERIFIED"
            );
            repoSession1.save(booking);

            if (repoSession1.count() != 1) {
                throw new AssertionError("Session 1: expected 1 booking in repo");
            }

            // 2. Simulate complete JVM crash / reboot: instantiate fresh repo with same disk file
            BookingRepository repoSession2 = new BookingRepository(tempFile);

            if (repoSession2.count() != 1) {
                throw new AssertionError("Session 2 (post-reboot): expected 1 booking loaded from disk WAL, got: " + repoSession2.count());
            }

            Optional<Booking> recoveredOpt = repoSession2.findByOrderId("ORD-DISK-001");
            if (recoveredOpt.isEmpty()) {
                throw new AssertionError("Session 2: failed to recover booking ORD-DISK-001 from disk!");
            }

            Booking recovered = recoveredOpt.get();
            if (!"BKG-TEST-DISK-1".equals(recovered.getBookingId()) ||
                recovered.getStartSeat() != 50 ||
                recovered.getEndSeat() != 54 ||
                recovered.getAmountPaid() != 2500.00 ||
                !"CONFIRMED".equals(recovered.getStatus())) {
                throw new AssertionError("Session 2: recovered booking data does not match original saved record!");
            }

        } catch (IOException e) {
            throw new RuntimeException("Disk persistence test error: " + e.getMessage(), e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public static void main(String[] args) {
        runAllTests();
    }
}
