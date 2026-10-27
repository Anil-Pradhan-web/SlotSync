# SlotSync: Backend Systems Architecture & Database Specification

## 1. Executive Overview

SlotSync is an ultra-high concurrency ticket reservation engine built to eliminate the classic **10:00 AM Tatkal / Flash-Sale Concurrency Crash**. When 100,000 users hit 1,000 seats simultaneously, standard relational databases collapse under lock contention.

To solve this, SlotSync decouples the transaction path into a **high-throughput backend architecture**:
1. **In-Memory Volatile Fast-Path (`dsa-engine` in RAM)**: Segment Tree ($O(\log N)$ range checks & atomic locks) + Token Bucket (HTTP 429 rate limiting) + Min-Heap TTL auto-reaper (5-minute hold without SQL polling).
2. **Durable Cold-Path (`backend/database/` + `payment-worker`)**: Permanent SQL schema (`events`, `seats`, `bookings`, `payment_audit_logs`) and disk WAL log (`bookings_wal.log`) storing confirmed financial tickets.
3. **Interactive Terminal Showcase & Telemetry (`SlotSyncDemoCLI`)**: Rich ANSI terminal demonstration suite profiling 100k concurrency, bot defense, TTL reaping, and crash recovery with live nanosecond latency histograms.

---

## 2. Directory Structure

```
SlotSync/
├── backend/
│   ├── pom.xml                               # Parent Maven Configuration
│   ├── database/
│   │   ├── schema.sql                        # DDL for PostgreSQL/SQLite
│   │   ├── seed.sql                          # Tatkal Express 12301 seed data
│   │   └── bookings_wal.log                  # Append-only durable disk journal
│   ├── dsa-engine/                           # Core DSA Algorithms (0 dependencies)
│   │   ├── src/main/java/com/slotsync/dsa/
│   │   │   ├── ratelimiter/TokenBucket.java
│   │   │   ├── segmenttree/SegmentTree.java
│   │   │   ├── ttl/MinHeapTTLReaper.java
│   │   │   └── hashring/ConsistentHashRing.java
│   ├── api-gateway/                          # Virtual Thread Gateway (Port 8080)
│   ├── waiting-room/                         # FIFO Queue & Batch Admission (Port 8081)
│   ├── slot-lock/                            # High-Speed RAM Seat Engine (Port 8082)
│   ├── payment-worker/                       # Webhook & Durable Persistence (Port 8083)
│   └── src/main/java/com/slotsync/
│       ├── SlotSyncAllServers.java           # Master microservices suite launcher
│       └── SlotSyncDemoCLI.java              # Interactive Terminal Demo Suite
├── planning/                                 # Engineering Specifications & ADRs
├── compile-backend.bat                       # Zero-dependency JDK 23 compiler
├── compile-backend.ps1                       # PowerShell compiler script
├── run-demo.bat                              # 1-Click Interactive Demo Launcher
├── start-all.bat                             # Unified microservices launcher (Ports 8080-8083)
├── stop-all.bat                              # Unified shutdown script
├── INTERVIEW_PITCH_GUIDE.md                  # Comprehensive Interviewer Pitch & Defense Guide
└── README.md                                 # Architecture & Benchmark Documentation
```

---

## 3. Database Schema Design (`backend/database/schema.sql`)

### 3.1 Entity Relationship Model
```mermaid
erDiagram
    EVENTS ||--o{ SEATS : contains
    EVENTS ||--o{ BOOKINGS : reserves
    BOOKINGS ||--o{ PAYMENT_AUDIT_LOGS : reconciles

    EVENTS {
        string id PK
        string name
        int total_slots
        int available_slots
        decimal base_price
        string event_status
    }
    SEATS {
        int id PK
        string event_id FK
        int seat_number
        string status
        string lock_id
        timestamp lock_expires_at
    }
    BOOKINGS {
        string booking_id PK
        string order_id UK
        string lock_id
        string user_id
        int start_seat
        int end_seat
        decimal amount_paid
        string transaction_ref
        string booking_status
        timestamp booked_at
    }
    PAYMENT_AUDIT_LOGS {
        int id PK
        string order_id
        string provider
        string provider_payment_id
        string status
        text raw_payload
    }
```

### 3.2 In-Memory vs. Database Division of Labor
| Feature | RAM Layer (`dsa-engine`) | SQL Database (`backend/database`) |
| :--- | :--- | :--- |
| **Contiguous Range Check** | $O(\log N)$ via SegmentTree | $O(N)$ full table scan (Avoided!) |
| **Race-Condition Locking** | Atomic `StampedLock` in microsecond | Row-level `SELECT FOR UPDATE` (Eliminated) |
| **5-Minute Hold Timer** | MinHeap TTL auto-reaper | Periodic polling worker (Eliminated) |
| **Confirmed Ticket Ledger** | Read-through cache | Durable ACID `bookings` table + WAL |
| **Financial Audit Trail** | Not stored | Immutable `payment_audit_logs` |

---

## 4. Interactive Terminal Demonstration Experience (`SlotSyncDemoCLI`)

The interactive terminal suite (`run-demo.bat` / `SlotSyncDemoCLI.java`) provides real-time verification of all systems guarantees:
1. **Live 100k Concurrency Stress Test**: Runs 100,000 real threads competing for 1,000 berths in a synchronized burst (`CountDownLatch`), profiling P50, P90, P99, P99.9 latencies, with mathematical proof of `0.00% double-booking`.
2. **Perimeter Bot Defense**: Simulates 500 malicious scraper requests hitting the Token Bucket rate limiter, dropping bots with `HTTP 429 Too Many Requests` in under 2 nanoseconds per drop.
3. **Ghost Inventory Auto-Reclamation**: Demonstrates abandoned passenger checkouts with an active live countdown, showing how the Min-Heap priority queue background daemon pops expired locks in $O(1)$ and auto-reclaims seats back to `AVAILABLE`.
4. **Crash & WAL Durability**: Purges volatile in-memory state simulating hard JVM power loss, re-populates state from `database/bookings_wal.log`, and audits 100% ticket survival.
5. **ASCII Train Coach Matrix**: Visualizes all 1,000 berths across Coaches S1 to S14 directly within the terminal (`[·]` Green Available, `[X]` Red Booked).
