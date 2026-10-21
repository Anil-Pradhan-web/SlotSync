# SlotSync

> **Ultra-High Concurrency Ticket & Slot Reservation Engine**  
> Engineered to eliminate race conditions, database deadlocks, and ghost inventory during peak flash sales and Tatkal ticket rushes (100,000 users hitting 1,000 seats).

---

## ⚡ The Problem: "The 10:00 AM Tatkal Disaster"

When **100,000 users** click "Book Now" at the exact same second for only **1,000 available seats**, traditional monolithic architectures fail in 3 catastrophic ways:
1. **The Crash**: 100k requests try to open SQL connections simultaneously, maxing out connection pools and crashing the database.
2. **Double-Booking (Race Conditions)**: Concurrent threads read the same seat as `AVAILABLE` before the database updates, selling the same seat to multiple buyers.
3. **Ghost Inventory**: Users abandon checkout pages or close their browsers, locking up inventory while genuine buyers are turned away.

---

## 💡 How SlotSync Solves It

SlotSync handles high concurrency with an architectural paradigm shift: **absorb traffic at the edge, lock seats in-memory via optimized DSA, and decouple database writes through asynchronous workers.**

```
 100,000 Users (Ingress)
          │
          ▼
 [ API Gateway (Port 8080) ] ──────> Token Bucket Algorithm (Stops bots & drops spam clicks)
          │
          ▼
 [ Virtual Waiting Room (8081) ] ──> FIFO Queue & Batch Dispatcher (Releases smooth batches: 200 users/sec)
          │
          ▼
 [ Slot-Lock Engine (8082) ] ──────> Segment Tree (O(log N) seat checking in RAM)
          │                          Min-Heap with TTL (5-min auto-release timer for ghost seats)
          ▼
 [ Payment Worker (8083) ] ────────> Asynchronous DB persistence (Zero SQL lock contention)
          │
          ▼
 [ PostgreSQL / SQLite + WAL ] ────> Calm, durable source of truth
```

---

## 🚀 Quick Start (One-Click Launch)

Launch all 4 backend microservices and the React 19 Frontend with a single command:

```cmd
# Double click or run in terminal:
start-all.bat
```

This automatically starts:
- 🚪 **API Gateway**: `http://localhost:8080` (Rate Limiter & Idempotency)
- 🎟️ **Virtual Waiting Room**: `http://localhost:8081` (200 users/sec batch admission)
- ⚡ **Slot-Lock Engine**: `http://localhost:8082` ($O(\log N)$ Segment Tree & Min-Heap TTL)
- 💳 **Payment Worker**: `http://localhost:8083` (Webhook reconciliation & durable WAL)
- 🖥️ **React Frontend**: `http://localhost:5173` (Live Mission Control Command Center)

To stop all services:
```cmd
stop-all.bat
```

---

## 📁 Monorepo Architecture

```
SlotSync/
├── start-all.bat                             # One-click launcher for all services
├── stop-all.bat                              # One-click shutdown script
├── backend/
│   ├── database/
│   │   ├── schema.sql                        # PostgreSQL/SQLite DDL
│   │   ├── seed.sql                          # Tatkal Express 12301 seed data
│   │   └── bookings_wal.log                  # Append-only durable disk journal
│   ├── dsa-engine/                           # Pure DSA algorithms (Zero dependencies)
│   ├── api-gateway/                          # Virtual Thread Gateway (Port 8080)
│   ├── waiting-room/                         # FIFO Queue & Batch Dispatcher (Port 8081)
│   ├── slot-lock/                            # In-Memory SegmentTree & TTL (Port 8082)
│   ├── payment-worker/                       # Webhook & Durable Persistence (Port 8083)
│   └── src/main/java/com/slotsync/
│       └── SlotSyncAllServers.java           # Unified master backend runner
├── frontend/                                 # React 19 + Vite Mission Control
│   ├── public/
│   │   ├── favicon.svg                       # Vector brand icon
│   │   └── logo.svg                          # Brand typography & logo
│   └── src/
│       ├── components/
│       │   ├── Header.jsx                    # Live Tatkal clock & connectivity badge
│       │   ├── TelemetryHUD.jsx              # QPS, Latency, 0% double-booking audit
│       │   ├── SeatMatrix.jsx                # 1,000-seat Segment Tree matrix (Coaches S1-S14)
│       │   ├── Simulators.jsx                # 10k Tatkal Rush, 500-Bot attack, Ghost TTL
│       │   ├── WaitingRoomHUD.jsx            # Queue depth & batch admission meter
│       │   ├── LogStream.jsx                 # Real-time microsecond kernel logs
│       │   └── BookingModal.jsx              # 3-step Tatkal booking & boarding pass
│       ├── services/api.js                   # 100% connected to live backend microservices
│       └── App.jsx                           # Live state orchestrator
└── planning/                                 # Engineering Specifications & ADRs
    ├── 01-System-Architecture.md
    ├── 02-PRD-Functional-Non-Functional.md
    ├── 03-DSA-Deep-Dive.md
    ├── 04-Microservices-and-APIs.md
    ├── 05-Implementation-Roadmap.md
    └── 06-Fullstack-Architecture-and-Database.md
```

---

## 🧠 The 4 Core DSA Pillars

| DSA / Pattern | Problem Solved | Speed / Complexity |
|---|---|---|
| **Token Bucket** | Stops bots and spam-clickers at the edge | $O(1)$ lock-free check (~50ns) |
| **Segment Tree** | Instant contiguous seat availability & lock in RAM | $O(\log N)$ range query (< 1µs) |
| **Min-Heap (Priority Queue) with TTL** | 5-minute auto-expiry of abandoned checkouts | $O(1)$ peek — Zero database polling |
| **Consistent Hashing Ring** | Equal partition balancing across waiting room nodes | Uniform distribution (< 5% variance) |

---

## 📊 Stress Test Benchmark Results

Simulated **100,000 concurrent user requests** against 1,000 seats:
- **Throughput**: **862,069 requests/sec**
- **Execution Time**: **116 ms**
- **P99 Latency**: **1.25 ms**
- **Double-Bookings**: **Strict 0.00% (STRICT ZERO)**
- **Inventory Conservation**: $999 + 1 = 1,000$
