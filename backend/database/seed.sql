-- ============================================================================
-- SlotSync Database Seed Data
-- 10:00 AM Tatkal Rush Seed: 1,000 Seats for 18463 Prashanti Express
-- (Bhubaneswar BBS -> KSR Bengaluru City SBC)
-- Pre-Booked: 200 Seats | Available: 800 Seats
-- ============================================================================

-- Insert the flagship event: Prashanti Express
INSERT INTO events (id, name, total_slots, available_slots, base_price, event_status, sale_start_time)
VALUES (
    'EVT-PRASHANTI-18463',
    '18463 - Prashanti Express (BBS -> SBC) - Tatkal Quota',
    1000,
    800,
    1450.00,
    'ACTIVE',
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- Seed initial bookings (Representative seed for 18463 Prashanti Express across coaches S1 to S14)
INSERT INTO bookings (booking_id, order_id, lock_id, user_id, event_id, start_seat, end_seat, amount_paid, transaction_ref, booking_status)
VALUES 
    ('BKG-1001', 'ORD-PRASHANTI-1001', 'LCK-SEED-1001', 'USR-PASSENGER-1001', 'EVT-PRASHANTI-18463', 4, 4, 1450.00, 'TXN_SEED_1001', 'CONFIRMED'),
    ('BKG-1002', 'ORD-PRASHANTI-1002', 'LCK-SEED-1002', 'USR-PASSENGER-1002', 'EVT-PRASHANTI-18463', 11, 11, 1450.00, 'TXN_SEED_1002', 'CONFIRMED'),
    ('BKG-1003', 'ORD-PRASHANTI-1003', 'LCK-SEED-1003', 'USR-PASSENGER-1003', 'EVT-PRASHANTI-18463', 23, 23, 1450.00, 'TXN_SEED_1003', 'CONFIRMED'),
    ('BKG-1004', 'ORD-PRASHANTI-1004', 'LCK-SEED-1004', 'USR-PASSENGER-1004', 'EVT-PRASHANTI-18463', 40, 40, 1450.00, 'TXN_SEED_1004', 'CONFIRMED'),
    ('BKG-1005', 'ORD-PRASHANTI-1005', 'LCK-SEED-1005', 'USR-PASSENGER-1005', 'EVT-PRASHANTI-18463', 58, 58, 1450.00, 'TXN_SEED_1005', 'CONFIRMED')
ON CONFLICT (booking_id) DO NOTHING;

-- Payment Audit Logs
INSERT INTO payment_audit_logs (order_id, provider, provider_payment_id, status, raw_payload)
VALUES
    ('ORD-PRASHANTI-1001', 'RAZORPAY', 'pay_prashanti_1001', 'SUCCESS', '{"method": "UPI", "vpa": "passenger@oksbi", "amount": 145000}'),
    ('ORD-PRASHANTI-1002', 'STRIPE',   'pi_prashanti_1002',  'SUCCESS', '{"method": "CARD", "last4": "4242", "amount": 145000}'),
    ('ORD-PRASHANTI-1003', 'UPI_MOCK', 'upi_prashanti_1003', 'SUCCESS', '{"method": "UPI", "vpa": "passenger@okaxis", "amount": 145000}');
