-- ============================================================================
-- SlotSync Database Seed Data
-- 10:00 AM Tatkal Rush Seed: 1,000 Seats for Rajdhani Express
-- ============================================================================

-- Insert the flagship event: Tatkal Express
INSERT INTO events (id, name, total_slots, available_slots, base_price, event_status, sale_start_time)
VALUES (
    'EVT-TATKAL-12301',
    'Rajdhani Express 12301 (NDLS -> HWH) - Tatkal Quota',
    1000,
    994,
    1450.00,
    'ACTIVE',
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

-- Seed initial bookings (Rahul, Priya, and Amit from our Tatkal story!)
INSERT INTO bookings (booking_id, order_id, lock_id, user_id, event_id, start_seat, end_seat, amount_paid, transaction_ref, booking_status)
VALUES 
    ('BKG-1001-RAHUL', 'ORD-TATKAL-001', 'LCK-001', 'USR-RAHUL-SHARMA', 'EVT-TATKAL-12301', 0, 1, 2900.00, 'TXN_UPI_RAHUL_98213', 'CONFIRMED'),
    ('BKG-1002-PRIYA', 'ORD-TATKAL-002', 'LCK-002', 'USR-PRIYA-VERMA',  'EVT-TATKAL-12301', 2, 3, 2900.00, 'TXN_UPI_PRIYA_48721', 'CONFIRMED'),
    ('BKG-1003-AMIT',  'ORD-TATKAL-003', 'LCK-003', 'USR-AMIT-PATEL',  'EVT-TATKAL-12301', 4, 5, 2900.00, 'TXN_NET_AMIT_11094',  'CONFIRMED')
ON CONFLICT (booking_id) DO NOTHING;

-- Payment Audit Logs for the initial bookings
INSERT INTO payment_audit_logs (order_id, provider, provider_payment_id, status, raw_payload)
VALUES
    ('ORD-TATKAL-001', 'RAZORPAY', 'pay_rahul_99120', 'SUCCESS', '{"method": "UPI", "vpa": "rahul@okaxis", "amount": 290000}'),
    ('ORD-TATKAL-002', 'STRIPE',   'pi_priya_55310',  'SUCCESS', '{"method": "CARD", "last4": "4242", "amount": 290000}'),
    ('ORD-TATKAL-003', 'UPI_MOCK', 'upi_amit_11204',  'SUCCESS', '{"method": "UPI", "vpa": "amit@oksbi", "amount": 290000}');
