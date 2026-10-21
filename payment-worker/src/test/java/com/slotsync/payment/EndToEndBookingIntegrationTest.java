package com.slotsync.payment;

import com.slotsync.lock.engine.SlotLockEngine;
import com.slotsync.payment.model.Booking;
import com.slotsync.payment.repository.BookingRepository;
import com.slotsync.payment.service.PaymentService;
import com.slotsync.waitingroom.model.QueueTicket;
import com.slotsync.waitingroom.service.QueueService;

import java.util.List;
import java.util.Optional;

/**
 * Full End-to-End Integration Test for SlotSync.
 * 
 * Verifies the complete lifecycle:
 * User Enters Queue -> Admitted via Batch -> Checks RAM Seats -> Locks Seats (5m TTL) -> Pays Webhook -> Ticket Persisted.
 */
public class EndToEndBookingIntegrationTest {

    public static void runAllTests() {
        System.out.println("--> Running EndToEndBookingIntegrationTest...");
        testPaymentSettlementAndIdempotency();
        testCompleteUserJourneyEndToEnd();
        System.out.println("  [PASS] All EndToEndBookingIntegrationTest tests passed successfully!\n");
    }

    public static void testPaymentSettlementAndIdempotency() {
        try (SlotLockEngine engine = new SlotLockEngine(50)) {
            BookingRepository repo = new BookingRepository();
            PaymentService paymentService = new PaymentService(engine, repo);

            // 1. Lock seats
            SlotLockEngine.LockResult lockResult = engine.reserveSlots("user-1", 10, 12, 300_000L);
            if (!lockResult.success()) {
                throw new AssertionError("Seat lock failed");
            }

            // 2. Process webhook payment
            PaymentService.SettlementResult result1 = paymentService.processPaymentWebhook(
                lockResult.lockId(), "user-1", 10, 12, 1500.0, "tx_12345"
            );
            if (!result1.success()) {
                throw new AssertionError("Payment settlement failed: " + result1.message());
            }

            Booking booking = result1.booking();
            if (booking == null || !"CONFIRMED".equals(booking.getStatus())) {
                throw new AssertionError("Booking status must be CONFIRMED");
            }
            if (repo.count() != 1) {
                throw new AssertionError("Expected 1 booking in repository");
            }

            // 3. Duplicate webhook (Bank network retry / Idempotency)
            PaymentService.SettlementResult result2 = paymentService.processPaymentWebhook(
                lockResult.lockId(), "user-1", 10, 12, 1500.0, "tx_12345"
            );
            if (!result2.success() || !"ALREADY_CONFIRMED".equals(result2.message())) {
                throw new AssertionError("Duplicate webhook must be recognized as ALREADY_CONFIRMED");
            }
            if (repo.count() != 1) {
                throw new AssertionError("Duplicate webhook MUST NOT create duplicate bookings in DB!");
            }
        }
    }

    public static void testCompleteUserJourneyEndToEnd() {
        // Step 1: Initialize the 3 microservice engines
        QueueService waitingRoom = new QueueService();
        try (SlotLockEngine lockEngine = new SlotLockEngine(1000)) {
            BookingRepository db = new BookingRepository();
            PaymentService paymentWorker = new PaymentService(lockEngine, db);

            // Persona: Rahul during 10:00 AM Tatkal rush
            String rahulUserId = "rahul-tatkal-user";

            // Journey Step 1: Rahul joins waiting line
            QueueTicket rahulTicket = waitingRoom.enqueue(rahulUserId, 101L);
            if (waitingRoom.getQueuePosition(rahulTicket.getTicketId()) != 1) {
                throw new AssertionError("Rahul should be #1 in line");
            }

            // Journey Step 2: Line manager admits batch of users (200 users/sec)
            List<QueueTicket> admittedBatch = waitingRoom.admitBatch(200, 60_000L);
            if (admittedBatch.isEmpty()) {
                throw new AssertionError("Admitted batch should not be empty");
            }
            QueueTicket rahulAdmitted = waitingRoom.getTicket(rahulTicket.getTicketId());
            if (!rahulAdmitted.isAdmissionValid()) {
                throw new AssertionError("Rahul must possess a valid admission token!");
            }

            // Journey Step 3: Rahul checks contiguous seat availability (Seats 45 to 48)
            int freeInGoalRange = lockEngine.checkAvailability(45, 48);
            if (freeInGoalRange != 4) {
                throw new AssertionError("Expected 4 seats free in range [45, 48]");
            }

            // Journey Step 4: Rahul atomically locks Seats 45 to 48 (5-minute hold)
            SlotLockEngine.LockResult lockResult = lockEngine.reserveSlots(rahulUserId, 45, 48, 300_000L);
            if (!lockResult.success()) {
                throw new AssertionError("Rahul failed to lock seats 45 to 48");
            }

            // Invariant check: Total free seats decremented from 1,000 to 996
            if (lockEngine.getTotalAvailableSeats() != 996) {
                throw new AssertionError("Expected 996 seats available after locking 4");
            }

            // Invariant check: Competing buyer tries to steal Seat 46 -> MUST BE REJECTED!
            SlotLockEngine.LockResult competingLock = lockEngine.reserveSlots("competing-buyer", 46, 50, 300_000L);
            if (competingLock.success()) {
                throw new AssertionError("OVERBOOKING ANOMALY! Competing buyer was able to steal seat 46!");
            }

            // Journey Step 5: Rahul pays via UPI / Bank Webhook
            PaymentService.SettlementResult paymentResult = paymentWorker.processPaymentWebhook(
                lockResult.lockId(), rahulUserId, 45, 48, 2400.00, "upi_tatkal_998811"
            );
            if (!paymentResult.success()) {
                throw new AssertionError("Rahul's payment settlement failed: " + paymentResult.message());
            }

            // Journey Step 6: Verify ticket is permanently persisted in DB
            Booking confirmedBooking = paymentResult.booking();
            Optional<Booking> fetchedFromDb = db.findByOrderId(confirmedBooking.getOrderId());
            if (fetchedFromDb.isEmpty()) {
                throw new AssertionError("Confirmed ticket was not found in permanent database!");
            }

            Booking ticket = fetchedFromDb.get();
            if (ticket.getStartSeat() != 45 || ticket.getEndSeat() != 48) {
                throw new AssertionError("Ticket seat range mismatch! Expected 45-48, got: " 
                    + ticket.getStartSeat() + "-" + ticket.getEndSeat());
            }
        }
    }

    public static void main(String[] args) {
        runAllTests();
    }
}
