package com.slotsync.waitingroom;

import com.slotsync.waitingroom.model.QueueTicket;
import com.slotsync.waitingroom.service.QueueService;

import java.util.List;

/**
 * Unit tests for QueueService and FIFO ordering.
 */
public class QueueServiceTest {

    public static void runAllTests() {
        System.out.println("--> Running QueueServiceTest...");
        testFIFOOrderAndPositions();
        testBatchAdmission();
        System.out.println("  [PASS] All QueueService tests passed successfully!\n");
    }

    public static void testFIFOOrderAndPositions() {
        QueueService service = new QueueService();

        QueueTicket t1 = service.enqueue("user-1", 101L);
        QueueTicket t2 = service.enqueue("user-2", 101L);
        QueueTicket t3 = service.enqueue("user-3", 101L);

        if (service.getQueuePosition(t1.getTicketId()) != 1) {
            throw new AssertionError("user-1 must be at position 1");
        }
        if (service.getQueuePosition(t2.getTicketId()) != 2) {
            throw new AssertionError("user-2 must be at position 2");
        }
        if (service.getQueuePosition(t3.getTicketId()) != 3) {
            throw new AssertionError("user-3 must be at position 3");
        }
    }

    public static void testBatchAdmission() {
        QueueService service = new QueueService();
        for (int i = 0; i < 500; i++) {
            service.enqueue("user_" + i, 101L);
        }

        if (service.getWaitingCount() != 500) {
            throw new AssertionError("Expected 500 waiting users");
        }

        // Admit first batch of 200 users
        List<QueueTicket> batch1 = service.admitBatch(200, 60_000L);
        if (batch1.size() != 200) {
            throw new AssertionError("Expected exactly 200 admitted users in batch 1, got: " + batch1.size());
        }

        for (QueueTicket admitted : batch1) {
            if (admitted.getStatus() != QueueTicket.Status.ADMITTED) {
                throw new AssertionError("Ticket status must be ADMITTED");
            }
            if (admitted.getAdmissionToken() == null || !admitted.getAdmissionToken().startsWith("adm_")) {
                throw new AssertionError("Invalid admission token: " + admitted.getAdmissionToken());
            }
        }

        if (service.getWaitingCount() != 300) {
            throw new AssertionError("Expected 300 users remaining in queue after admitting 200");
        }
    }

    public static void main(String[] args) {
        runAllTests();
    }
}
