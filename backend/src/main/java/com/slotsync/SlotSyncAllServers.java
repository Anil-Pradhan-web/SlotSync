package com.slotsync;

import com.slotsync.gateway.server.GatewayServer;
import com.slotsync.lock.engine.SlotLockEngine;
import com.slotsync.lock.server.SlotLockServer;
import com.slotsync.payment.model.Booking;
import com.slotsync.payment.repository.BookingRepository;
import com.slotsync.payment.server.PaymentServer;
import com.slotsync.payment.service.PaymentService;
import com.slotsync.waitingroom.dispatcher.BatchAdmissionDispatcher;
import com.slotsync.waitingroom.server.WaitingRoomServer;
import com.slotsync.waitingroom.service.QueueService;

import java.io.File;

/**
 * Master Application Launcher for all 4 SlotSync Backend Microservices.
 */
public class SlotSyncAllServers {

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("           SLOTSYNC — HIGH-CONCURRENCY BACKEND SUITE              ");
        System.out.println("==================================================================");

        try {
            // 1. Initialize SlotLockEngine with 1,000 seats
            SlotLockEngine lockEngine = new SlotLockEngine(1000);

            // 2. Initialize Durable Booking Database
            File dbWal = new File("database/bookings_wal.log");
            if (!dbWal.exists() && new File("backend/database/bookings_wal.log").exists()) {
                dbWal = new File("backend/database/bookings_wal.log");
            }
            BookingRepository bookingRepo = new BookingRepository(dbWal);

            // Seed initial bookings for Rahul, Priya, Amit if repository is empty
            if (bookingRepo.count() == 0) {
                Booking b1 = new Booking("BKG-1001-RAHUL", "ORD-TATKAL-001", "LCK-001", "USR-RAHUL-SHARMA", 0, 1, 2900.00, "TXN_UPI_RAHUL_98213");
                Booking b2 = new Booking("BKG-1002-PRIYA", "ORD-TATKAL-002", "LCK-002", "USR-PRIYA-VERMA",  2, 3, 2900.00, "TXN_UPI_PRIYA_48721");
                Booking b3 = new Booking("BKG-1003-AMIT",  "ORD-TATKAL-003", "LCK-003", "USR-AMIT-PATEL",  4, 5, 2900.00, "TXN_NET_AMIT_11094");
                bookingRepo.save(b1);
                bookingRepo.save(b2);
                bookingRepo.save(b3);
            }

            // Mark existing booked seats in the Segment Tree
            for (Booking b : bookingRepo.findAll()) {
                lockEngine.reserveSlots(b.getUserId(), b.getStartSeat(), b.getEndSeat(), 86400000L * 365);
                lockEngine.confirmReservation(b.getLockId());
            }

            // 3. Start Microservice 2: Virtual Waiting Room (Port 8081)
            QueueService queueService = new QueueService();
            BatchAdmissionDispatcher dispatcher = new BatchAdmissionDispatcher(queueService, 200, 60000L, null);
            dispatcher.start(1000L);
            WaitingRoomServer waitingRoomServer = new WaitingRoomServer(8081, queueService, dispatcher);
            waitingRoomServer.start();
            System.out.println("  [OK] Microservice 2: Waiting Room running on http://127.0.0.1:8081");

            // 4. Start Microservice 3: Slot-Lock Engine (Port 8082)
            SlotLockServer slotLockServer = new SlotLockServer(8082, lockEngine);
            slotLockServer.start();
            System.out.println("  [OK] Microservice 3: Slot-Lock Engine running on http://127.0.0.1:8082");

            // 5. Start Microservice 4: Payment Worker (Port 8083)
            PaymentService paymentService = new PaymentService(lockEngine, bookingRepo);
            PaymentServer paymentServer = new PaymentServer(8083, paymentService);
            paymentServer.start();
            System.out.println("  [OK] Microservice 4: Payment Worker running on http://127.0.0.1:8083");

            // 6. Start Microservice 1: API Gateway (Port 8080)
            GatewayServer gatewayServer = new GatewayServer(8080);
            gatewayServer.start();
            System.out.println("  [OK] Microservice 1: API Gateway running on http://127.0.0.1:8080");

            System.out.println("==================================================================");
            System.out.printf("  STATUS: ALL 4 SERVERS RUNNING (Available Seats: %d / %d)%n",
                    lockEngine.getTotalAvailableSeats(), lockEngine.getTotalSeats());
            System.out.println("  Press Ctrl+C to terminate all services.");
            System.out.println("==================================================================");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[SHUTDOWN] Stopping all microservices cleanly...");
                gatewayServer.stop();
                waitingRoomServer.stop();
                slotLockServer.stop();
                paymentServer.stop();
                System.out.println("[SHUTDOWN] All services stopped.");
            }));

            // Keep main thread alive
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println("[FATAL] Error starting SlotSync servers: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
