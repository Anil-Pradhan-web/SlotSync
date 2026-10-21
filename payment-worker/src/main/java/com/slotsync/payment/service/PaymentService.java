package com.slotsync.payment.service;

import com.slotsync.lock.engine.SlotLockEngine;
import com.slotsync.payment.model.Booking;
import com.slotsync.payment.repository.BookingRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Payment & Order Settlement Service (Worker 4: The Calm Bank Clerk).
 * 
 * Reconciles incoming bank webhook events, locks confirmation in the RAM engine,
 * and calmly writes confirmed bookings to durable storage.
 */
public class PaymentService {

    private final SlotLockEngine slotLockEngine;
    private final BookingRepository bookingRepository;

    public record SettlementResult(boolean success, Booking booking, String message) {}

    public PaymentService(SlotLockEngine slotLockEngine, BookingRepository bookingRepository) {
        this.slotLockEngine = slotLockEngine;
        this.bookingRepository = bookingRepository;
    }

    /**
     * Reconciles a bank payment webhook and creates a permanent confirmed booking.
     */
    public SettlementResult processPaymentWebhook(
            String lockId, 
            String userId, 
            int startSeat, 
            int endSeat, 
            double amount, 
            String transactionRef) {

        // Check if already processed (Idempotency)
        Optional<Booking> existing = bookingRepository.findByLockId(lockId);
        if (existing.isPresent()) {
            return new SettlementResult(true, existing.get(), "ALREADY_CONFIRMED");
        }

        // Step 1: Confirm reservation in RAM engine (cancels the 5-minute auto-expiry timer)
        boolean confirmedInEngine = slotLockEngine.confirmReservation(lockId);
        if (!confirmedInEngine) {
            return new SettlementResult(false, null, "LOCK_EXPIRED_OR_NOT_FOUND");
        }

        // Step 2: Generate permanent IDs and persist booking
        String bookingId = "BK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        String orderId = "ORD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();

        Booking booking = new Booking(bookingId, orderId, lockId, userId, startSeat, endSeat, amount, transactionRef);
        bookingRepository.save(booking);

        return new SettlementResult(true, booking, "BOOKING_CONFIRMED");
    }

    public Optional<Booking> getBooking(String orderId) {
        return bookingRepository.findByOrderId(orderId);
    }

    public BookingRepository getRepository() {
        return bookingRepository;
    }
}
