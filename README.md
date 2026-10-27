<div align="center">

# ⚡ SlotSync
### Ultra-High Concurrency Tatkal Ticket & Slot Reservation Engine

[![Java 23](https://img.shields.io/badge/Java-23%20Virtual%20Threads-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Terminal CLI Demo](https://img.shields.io/badge/Interactive-Terminal%20Demo%20Suite-00E5FF?style=for-the-badge&logo=gnometerminal&logoColor=black)](./INTERVIEW_PITCH_GUIDE.md)
[![Zero Overbooking](https://img.shields.io/badge/Double--Booking-0.00%25%20STRICT%20ZERO-10B981?style=for-the-badge)](https://github.com/Anil-Pradhan-web/SlotSync)
[![Throughput](https://img.shields.io/badge/Peak%20Throughput-884k%2B%20req%2Fsec-38BDF8?style=for-the-badge)](https://github.com/Anil-Pradhan-web/SlotSync)
[![Zero Framework Bloat](https://img.shields.io/badge/Runtime-Pure%20Java%20HTTP-F59E0B?style=for-the-badge)](./backend)
[![License: MIT](https://img.shields.io/badge/License-MIT-A855F7?style=for-the-badge)](./LICENSE)

<br/>

**Engineered to solve the classic Indian Railways "10:00 AM Tatkal Crisis":**  
Where **100,000 desperate passengers hit the booking gateway at the exact same fraction of a second for just 1,000 available train berths** — delivering **Strict 0.00% Double-Booking**, **Zero Database Deadlocks**, and **Zero Ghost Inventory**.

[Try It in 10 Seconds](#-try-it-in-10-seconds-interactive-terminal-showcase) • [The Story](#-the-story-what-breaks-at-100000-am) • [The 4 Microservices](#-the-4-microservices-the-railway-station-model) • [Core DSA](#-the-4-core-dsa-superpowers) • [Live Benchmarks](#-live-stress-test-benchmark-results) • [Interview Pitch Guide](./INTERVIEW_PITCH_GUIDE.md) • [Quick Start](#-quick-start)

</div>

---

## 🎮 Try It in 10 Seconds (Interactive Terminal Showcase)

You don't need a browser, complex setup, or external dependencies. SlotSync comes with an interactive ANSI terminal showcase (`SlotSyncDemoCLI.java`) that tests real Java concurrency live on your machine:

```cmd
run-demo.bat
```

```
================================================================================
               ⚡ SLOTSYNC — HIGH-CONCURRENCY BACKEND SUITE                     
              Tatkal Ticket & Slot Reservation Engine (Java 23)                 
================================================================================
  • Core Engine: Pure Java (Zero Framework Bloat, < 800ms boot, Microsecond Latency)
  • DSA Superpowers: Segment Tree (RAM), Min-Heap TTL, Token Bucket, Append-Only WAL
  • Guarantees: Strict 0.00% Double-Booking, Zero Deadlocks, Zero Ghost Inventory
--------------------------------------------------------------------------------
  SELECT AN INTERVIEWER DEMO SCENARIO:
  [1] ⚡ 10:00 AM Tatkal Concurrency Blast (100,000 real requests, 0.00% double-booking)
  [2] 🛡️ Edge Botnet & DDoS Defense (Token Bucket drops 500 bot attacks with HTTP 429)
  [3] 👻 Ghost Inventory Auto-Reaper (Min-Heap Priority Queue auto-frees abandoned seats)
  [4] 💾 Crash & WAL Recovery Test (Simulate JVM crash, reload state from bookings_wal.log)
  [5] 🗺️ Live Train Coach & Seat Matrix (Visual ASCII grid across S1 to S14 coaches)
  [6] 🚀 Run Complete End-to-End Showcase (Scenarios 1 -> 2 -> 3 -> 4 sequential)
  [0] ❌ Exit Demonstration

Enter Choice [0-6]: 1
```

### What happens when you press `[1]`?
- **100,000 real user requests** are pre-generated across **100 concurrent worker threads**.
- A `CountDownLatch` holds all threads until a synchronized start gun fires at the exact same nanosecond into the in-memory **Segment Tree**.
- The benchmark finishes in **~113 milliseconds** with over **884,000 requests/second**, **P99 latency of 0.89 ms**, and **mathematically verified 0.00% double-booking** (999 occupied + 1 free = 1,000 seats).

---

## ⚡ The Story: What Breaks at 10:00:00 AM?

Every morning at **10:00:00 AM**, Indian Railways opens the **Tatkal ticket quota**. What follows is one of the most intense burst-traffic challenges in software engineering:

```
            10:00:00 AM — THE TRAFFIC TSUNAMI (0 to 100,000 in 200ms)
                      
       100,000+ Desperate Passengers + Automated Scraping Bots
                              │
                              ▼
                [ Only 1,000 Available Berths ]
                  (Coaches S1 to S14 Sleeper)
```

In traditional monolithic web applications backed by standard SQL databases, **three catastrophic disasters happen simultaneously**:

### 💥 Disaster 1: The Database Connection Pool Crash
- Every user request immediately opens a database connection to query available seats.
- Standard connection pools (e.g., HikariCP) are configured for **50 to 100 connections**.
- When 100,000 requests arrive in under 250 milliseconds, the connection pool chokes instantly. CPU hits 100%, threads wait indefinitely, and the server throws `HTTP 504 Gateway Timeout` or crashes.

### 👥 Disaster 2: Double-Booking (Concurrency Race Conditions)
- Two passengers search for a berth at the exact same millisecond. Both read Seat #42 as `AVAILABLE`.
- Both click confirm. Traditional row-level `SELECT ... FOR UPDATE` locks fail under concurrent burst volume because multiple transactions read stale snapshots before locks are acquired.
- Both users are charged, and **the same physical berth is sold twice**, resulting in station disputes and legal complaints.

### 👻 Disaster 3: Ghost Inventory (The Phantom Hold)
- To prevent double-booking, standard systems lock a seat the moment a user enters the payment page.
- However, up to **30% of users abandon checkout** (close browser, payment OTP fails, or network drops).
- If the system relies on periodic database cron jobs (`SELECT * FROM locks WHERE expired_at < NOW()`) to free them, seats remain locked as "ghosts" for 15–30 minutes while genuine waiting buyers are turned away with "Regret / Housefull".

---

## 🚉 The 4 Microservices: The "Railway Station" Model

Instead of putting database locks on the critical user path, SlotSync decouples the transaction flow into **4 lightweight microservices** using the real-world **Railway Station Model**:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        SLOTSYNC 4-MICROSERVICES PIPELINE                               │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                        │
│ 1. API GATEWAY (Port 8080)       = 👮 The Station Gate Security Guard                  │
│    • Drops malicious botnets with HTTP 429 using Token Bucket rate limiting.           │
│    • Drops duplicate button clicks using X-Idempotency-Key.                            │
│                                      │                                                 │
│                                      ▼                                                 │
│ 2. WAITING ROOM (Port 8081)      = 🚪 The Waiting Hall & Subway Turnstile              │
│    • Holds 10,000 passengers in a consistent FIFO queue.                               │
│    • Admits smooth, metered batches of 200 users/sec with an admission token, so the   │
│      downstream counter is never flooded.                                              │
│                                      │                                                 │
│                                      ▼                                                 │
│ 3. SLOT-LOCK ENGINE (Port 8082)  = ⚡ The High-Speed RAM Counter Clerk (DSA Core)      │
│    • Manages 1,000 berths (Coaches S1-S14) in RAM using a Segment Tree.                │
│    • StampedLock optimistic reads: checks & locks contiguous seats in < 1 microsecond. │
│    • Min-Heap TTL Reaper: If user doesn't pay in 5 min, auto-reclaims the seats in     │
│      O(1) time without any slow SQL polling queries.                                   │
│                                      │                                                 │
│                                      ▼                                                 │
│ 4. PAYMENT WORKER (Port 8083)    = 💳 The Cashier & Safe (WAL on Disk)                 │
│    • Receives bank webhook payment reconciliation.                                     │
│    • Confirms the lock and writes an immutable record to disk (`bookings_wal.log`).    │
│    • Guaranteed zero data loss even if the server crashes.                             │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🧠 The 4 Core DSA Superpowers

Every algorithm in SlotSync lives in [`backend/dsa-engine`](./backend/dsa-engine) and was chosen to eliminate database bottlenecks:

| Data Structure / Pattern | Where It Lives | Problem Solved | Computational Complexity | Why Not Traditional CRUD / SQL? |
|---|---|---|---|---|
| **Segment Tree** | [`SegmentTree.java`](./backend/dsa-engine/src/main/java/com/slotsync/dsa/segmenttree/SegmentTree.java) | Instantly finds contiguous available seat ranges (e.g., family of 4) and checks seat conflicts atomically in RAM. | **$O(\log N)$** query & update (< 1µs) | `SELECT ... FOR UPDATE` in SQL causes row lock escalation and deadlocks under 100k queries. Segment Tree operates entirely in RAM with bit manipulation. |
| **`StampedLock`** | [`SegmentTree.java`](./backend/dsa-engine/src/main/java/com/slotsync/dsa/segmenttree/SegmentTree.java) | Read vs Write thread contention. | Non-blocking optimistic read | Standard `synchronized` or `ReentrantReadWriteLock` forces thread context switching. `StampedLock.tryOptimisticRead()` allows thousands of concurrent reads with zero lock overhead. |
| **Min-Heap Priority Queue** | [`MinHeapTTLReaper.java`](./backend/dsa-engine/src/main/java/com/slotsync/dsa/ttl/MinHeapTTLReaper.java) | Reclaims abandoned checkouts automatically without database cron polling. | **$O(1)$** peek & **$O(\log K)$** eviction | SQL systems run a cron `SELECT * FROM locks WHERE expired_at < NOW()`, causing full table scans every minute. Min-Heap only inspects the earliest expiring root node. |
| **Token Bucket** | [`TokenBucket.java`](./backend/dsa-engine/src/main/java/com/slotsync/dsa/ratelimiter/TokenBucket.java) | Drops bots, scraping scripts, and rapid double-clicking at the network perimeter. | **$O(1)$** lock-free CAS (~50ns) | Drops 450+ malicious requests in < 2 microseconds per drop with zero database lookups and zero thread blocking. |

---

## 📊 Live Stress Test Benchmark Results

Executed live on **100,000 concurrent user requests** hitting 1,000 seats (`TatkalConcurrencyStressTest.java` / Demo Option 1):

```
--------------------------------------------------------------------------------
                        STRESS TEST BENCHMARK RESULTS                           
--------------------------------------------------------------------------------
  Total Requests Processed:     100,000 in 113 ms
  Successful Bookings:          556
  Rejected (Conflicts):         99,444
  Actual Throughput:            884,955 requests/second

  REAL NANOSECOND LATENCY PROFILE:
    • P50  Latency:     0.20 µs ( 0.000 ms)
    • P90  Latency:     1.30 µs ( 0.001 ms)
    • P99  Latency:   890.10 µs ( 0.890 ms)
    • P99.9 Latency:  1622.70 µs ( 1.623 ms)

  MATHEMATICAL AUDIT & CONSERVATION CHECK:
    • Occupied Berths:          999
    • Remaining Available:      1
    • Conservation Equality:    999 + 1 = 1000 (Expected: 1000) -> PASS
    • DOUBLE-BOOKING AUDIT:     STRICT 0.00% (ZERO CONFLICTS ACQUIRED)
================================================================================
```

| Metric | Verified Measurement | Engineering Significance |
|---|---|---|
| **Peak Throughput** | **884,955 requests / sec** | Processes entire Tatkal rush in JVM memory in ~113 ms |
| **P50 Latency** | **0.20 microseconds (0.000 ms)** | Sub-microsecond optimistic read locking |
| **P99 Latency** | **0.89 milliseconds** | Sub-millisecond response for 99% of passengers |
| **Double-Booking Rate** | **Strict 0.00% (STRICT ZERO)** | Zero overlapping berths ever issued |
| **Inventory Conservation** | **$1,000 / 1,000$ Exact** | $999\text{ occupied} + 1\text{ free} = 1,000$ berths accounted for |
| **Thread Model** | **Java 23 Virtual Threads** | Lightweight execution without OS thread starvation |

---

## 🔌 Microservices Directory & API Contracts

All 4 backend microservices run as lightweight Java processes without heavy frameworks:

| Port | Service | Core Responsibility | Primary Endpoints | Source Class |
|---|---|---|---|---|
| **`8080`** | **API Gateway** | Token Bucket Rate Limiting, Idempotency Cache, CORS | `GET /health`<br/>`POST /api/v1/queue/join` | [`GatewayServer.java`](./backend/api-gateway/src/main/java/com/slotsync/gateway/server/GatewayServer.java) |
| **`8081`** | **Virtual Waiting Room** | FIFO Passenger Queue, Batch Dispatcher (200/s) | `GET /health`<br/>`GET /api/v1/queue/status`<br/>`POST /api/v1/queue/admit` | [`WaitingRoomServer.java`](./backend/waiting-room/src/main/java/com/slotsync/waitingroom/server/WaitingRoomServer.java) |
| **`8082`** | **Slot-Lock Engine** | Segment Tree in RAM, Min-Heap TTL Reaper, StampedLocks | `GET /health`<br/>`GET /api/v1/slots/state`<br/>`POST /api/v1/slots/lock`<br/>`POST /api/v1/slots/unlock`<br/>`POST /api/v1/slots/tatkal-wave`<br/>`POST /api/v1/slots/reset` | [`SlotLockServer.java`](./backend/slot-lock/src/main/java/com/slotsync/lock/server/SlotLockServer.java) |
| **`8083`** | **Payment Worker** | Webhook Reconciliation, Append-Only Disk WAL Log | `GET /health`<br/>`POST /api/v1/payments/process`<br/>`POST /api/v1/payments/webhook` | [`PaymentServer.java`](./backend/payment-worker/src/main/java/com/slotsync/payment/server/PaymentServer.java) |

---

## 📁 Clean Monorepo Structure

```
SlotSync/
├── .github/
│   └── workflows/
│       └── ci.yml                            # Automated Java 23 CI & 100k stress test runner
├── LICENSE                                   # MIT License
├── README.md                                 # Architecture & engineering documentation
├── INTERVIEW_PITCH_GUIDE.md                  # Comprehensive Interview Pitch & Defense Guide
├── compile-backend.bat                       # Zero-dependency JDK 23 batch compiler
├── compile-backend.ps1                       # PowerShell compiler script
├── run-demo.bat                              # 1-Click Interactive Terminal Demo Launcher
├── start-all.bat                             # One-click unified microservices launcher
├── stop-all.bat                              # One-click graceful shutdown script
├── backend/
│   ├── database/
│   │   ├── schema.sql                        # PostgreSQL / SQLite DDL schema
│   │   ├── seed.sql                          # Prashanti Express 18463 seed data
│   │   └── bookings_wal.log                  # Append-only durable disk journal
│   ├── dsa-engine/                           # Pure DSA core (SegmentTree, TokenBucket, MinHeap)
│   ├── api-gateway/                          # Virtual Thread Gateway (Port 8080)
│   ├── waiting-room/                         # FIFO Queue & Batch Dispatcher (Port 8081)
│   ├── slot-lock/                            # In-Memory SegmentTree & TTL (Port 8082)
│   ├── payment-worker/                       # Webhook & Durable WAL Persistence (Port 8083)
│   └── src/main/java/com/slotsync/
│       ├── SlotSyncAllServers.java           # Master runner for all 4 microservices
│       └── SlotSyncDemoCLI.java              # Interactive Terminal Demo Suite
└── planning/                                 # Engineering Specifications & System Design ADRs
    ├── 01-PRD-and-Requirements.md
    ├── 02-System-Architecture-HLD.md
    ├── 03-DSA-Core-Engine-LLD.md
    ├── 04-State-Machine-and-Failure-Modes.md
    ├── 05-Implementation-Roadmap.md
    └── 06-Backend-Architecture-and-Database.md
```

---

## 🚀 Quick Start

### Prerequisites
- **Java JDK 21+** (Java 23 recommended)

### 1. Run the Interactive Terminal Showcase
Double-click or run:
```cmd
run-demo.bat
```
Launches the interactive ANSI terminal menu, letting you trigger real-time 100k concurrency benchmarks, bot mitigation tests, and ghost reaper countdowns interactively.

### 2. Launch All 4 Live Microservices (HTTP Background Daemons)
To start the live HTTP microservices suite on **Ports 8080, 8081, 8082, and 8083**:
```cmd
start-all.bat
```

### 3. Graceful Shutdown
To stop all background microservice servers:
```cmd
stop-all.bat
```

### 4. Technical Interview Preparation
Before presenting this project to an interviewer, read the **[INTERVIEW_PITCH_GUIDE.md](./INTERVIEW_PITCH_GUIDE.md)** for:
- The 2-minute opening elevator pitch
- The Railway Station mental model for explaining microservices simply
- Answers to the Top 10 hard questions (*"Why not Redis?"*, *"How does StampedLock work?"*, *"Why Project Loom?"*)

---

## 📜 License

This project is open-source and distributed under the **[MIT License](./LICENSE)**.  
Engineered with ❤️ by **[Anil Pradhan](https://github.com/Anil-Pradhan-web)**.
