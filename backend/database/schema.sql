-- ============================================================================
-- SlotSync Database Schema (PostgreSQL / SQLite compatible)
-- Durable Storage for Ultra-High Concurrency Reservation Engine
-- ============================================================================

-- 1. Events / Slot Collections (e.g., IRCTC Train, Coldplay Concert, Tatkal Flash Sale)
CREATE TABLE IF NOT EXISTS events (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    total_slots INT NOT NULL CHECK (total_slots > 0),
    available_slots INT NOT NULL CHECK (available_slots >= 0),
    base_price DECIMAL(10, 2) NOT NULL DEFAULT 500.00,
    event_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, PAUSED, COMPLETED
    sale_start_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Granular Seats / Inventory Units
CREATE TABLE IF NOT EXISTS seats (
    id SERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    seat_number INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE', -- AVAILABLE, LOCKED, BOOKED
    locked_by VARCHAR(64),
    lock_id VARCHAR(64),
    lock_expires_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_event_seat UNIQUE(event_id, seat_number)
);

CREATE INDEX IF NOT EXISTS idx_seats_event_status ON seats(event_id, status);
CREATE INDEX IF NOT EXISTS idx_seats_lock_expires ON seats(lock_expires_at);

-- 3. Confirmed Bookings (Permanent Durable Ledger)
CREATE TABLE IF NOT EXISTS bookings (
    booking_id VARCHAR(64) PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    lock_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL REFERENCES events(id),
    start_seat INT NOT NULL,
    end_seat INT NOT NULL,
    seat_count INT GENERATED ALWAYS AS (end_seat - start_seat + 1) STORED,
    amount_paid DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'INR',
    transaction_ref VARCHAR(128) NOT NULL,
    booking_status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED', -- CONFIRMED, REFUNDED, CANCELLED
    booked_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bookings_user ON bookings(user_id);
CREATE INDEX IF NOT EXISTS idx_bookings_order ON bookings(order_id);
CREATE INDEX IF NOT EXISTS idx_bookings_event ON bookings(event_id);

-- 4. Payment Audit Logs (Immutable Financial Traceability)
CREATE TABLE IF NOT EXISTS payment_audit_logs (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    provider VARCHAR(32) NOT NULL, -- RAZORPAY, STRIPE, UPI_MOCK
    provider_payment_id VARCHAR(128) NOT NULL,
    signature VARCHAR(256),
    status VARCHAR(32) NOT NULL, -- SUCCESS, FAILED, TIMEOUT
    raw_payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_order ON payment_audit_logs(order_id);

-- 5. Rate Limiter & Security Incident Log (Bot throttling history)
CREATE TABLE IF NOT EXISTS security_audit_logs (
    id SERIAL PRIMARY KEY,
    client_ip VARCHAR(64) NOT NULL,
    endpoint VARCHAR(128) NOT NULL,
    http_status INT NOT NULL,
    reason VARCHAR(128) NOT NULL, -- TOKEN_BUCKET_EXHAUSTED, IDEMPOTENCY_DUPLICATE
    occurred_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_security_ip ON security_audit_logs(client_ip);
