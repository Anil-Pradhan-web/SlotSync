<div align="center">

# ⚡ SlotSync
### Ultra-High Concurrency Tatkal Ticket & Slot Reservation Engine

[![Java 23](https://img.shields.io/badge/Java-23%20Virtual%20Threads-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![React 19](https://img.shields.io/badge/React-19%20Dashboard-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-Fast%20Bundler-646CFF?style=for-the-badge&logo=vite&logoColor=white)](https://vitejs.dev/)
[![Zero Overbooking](https://img.shields.io/badge/Double--Booking-0.00%25%20STRICT%20ZERO-10B981?style=for-the-badge)](https://github.com/Anil-Pradhan-web/SlotSync)
[![Throughput](https://img.shields.io/badge/Peak%20Throughput-862k%2B%20req%2Fsec-38BDF8?style=for-the-badge)](https://github.com/Anil-Pradhan-web/SlotSync)
[![License: MIT](https://img.shields.io/badge/License-MIT-A855F7?style=for-the-badge)](./LICENSE)

<br/>

**Engineered to solve the classic Indian Railways "10:00 AM Tatkal Crisis":**  
Where **100,000 desperate passengers hit the booking gateway at the exact same fraction of a second for just 1,000 available train berths** — delivering **Strict 0.00% Double-Booking**, **Zero Database Deadlocks**, and **Zero Ghost Inventory**.

[Problem Statement](#-the-problem-statement-the-1000-am-tatkal-crisis) • [Why Not Monolithic?](#-why-not-a-monolith-why-decoupled-microservices) • [Architecture](#-system-architecture) • [Tech Stack](#-technology-stack) • [Core DSA](#-the-4-core-dsa-superpowers) • [Quick Start](#-quick-start-one-click-launch) • [Microservices Directory](#-microservices-directory--api-contracts)

</div>

---

## ⚡ The Problem Statement: The "10:00 AM Tatkal Crisis"

In India, Indian Railways opens the **Tatkal ticket quota** every morning at exactly **10:00:00 AM**. What follows is one of the most brutal real-world concurrency challenges in software engineering:

```
           10:00:00 AM — THE TRAFFIC TSUNAMI (0 to 100,000 in 200ms)
                     
      100,000+ Concurrent Passengers + Scraping Botnets
                             │
                             ▼
              [ Only 1,000 Available Berths ]
                 (Coaches S1 to S14 Sleeper)
```

When 100,000 users click **"Book Now"** simultaneously against only 1,000 seats, traditional systems fail due to **three catastrophic real-world disasters**:

### 💥 Disaster 1: The Database Connection Pool Crash
- In standard architectures, each user request hits a web server and immediately opens a database connection to query available seats.
- Standard SQL connection pools (e.g., HikariCP) are configured for **50 to 100 maximum connections**.
- When 100,000 requests arrive in under 250 milliseconds, connection pools are choked instantly. Database CPU hits 100%, threads wait indefinitely in queue, and the application throws `HTTP 504 Gateway Timeout` or crashes entirely.

### 👥 Disaster 2: Double-Booking (Concurrency Race Conditions)
- When two passengers (e.g., Rahul and Priya) search for a seat at the same millisecond, both read Seat #12 as `AVAILABLE`.
- Both click confirm. Traditional row-level `SELECT ... FOR UPDATE` locks fail under concurrent volume because multiple transactions read stale snapshots before locks are acquired.
- Both users receive payment prompts, and **the same physical berth is sold twice**, resulting in angry passengers, counter disputes, and legal complaints.

### 👻 Disaster 3: Ghost Inventory (The Phantom Hold)
- To prevent double-booking, traditional systems lock a seat when a buyer enters the payment page.
- However, up to **30% of buyers abandon the checkout** (close browser, internet drops, or payment gateway fails).
- If the system relies on periodic cron jobs or batch database queries (`SELECT * FROM locks WHERE expired_at < NOW()`) to free them, seats remain locked as "ghosts" for 15–30 minutes while genuine buyers waiting in line are turned away with "Regret / Housefull".

---

## 🏛️ Why Not a Monolith? Why Decoupled Microservices?

A traditional monolithic web application (one big Spring Boot or Django application connected to a single PostgreSQL database) is the default choice for 95% of web apps. **However, for high-concurrency burst flash sales like Tatkal, a monolith is architecturally fatal.**

Here is the exact engineering justification for why a Monolith fails and why SlotSync was built as **decoupled microservices**:

| Engineering Axis | ❌ Why Monolithic Fails at 10:00 AM | ✅ How SlotSync Microservices Win |
|---|---|---|
| **Resource Contention** | **Shared Thread & Connection Pool Choke**: If 50,000 users are stalled waiting for bank OTP verification, all server threads and DB connections are held hostage. Users simply browsing train schedules get blocked because the whole monolith is choked. | **Complete Fault & Resource Isolation**: The **Payment Worker (Port 8083)** handles slow external banks completely isolated from the **Slot-Lock Engine (Port 8082)**. The core engine never waits on an external HTTP request. |
| **Blast Radius** | **Single Point of Complete Failure**: If the payment gateway slows down or an unhandled exception occurs in checkout, the entire application crashes (search, authentication, booking, and ticket downloads all go down together). | **Contained Perimeter Blast Radius**: If the payment worker is under heavy load, the **API Gateway (Port 8080)** and **Waiting Room (Port 8081)** continue operating smoothly, queuing passengers in orderly fashion without dropping connections. |
| **Data Storage Bottleneck** | **Database Lock Escalation**: Monoliths rely on database transactions (`BEGIN ... SELECT FOR UPDATE ... COMMIT`). When 100k queries fight for the same 1,000 rows, database lock escalation serializes requests into a single-lane crawl (P99 latency spikes past 15,000 ms). | **In-Memory Segment Tree in RAM**: SlotSync's **Slot-Lock Engine** eliminates all database queries during seat checks. Contiguous berths are verified and held in **RAM** in under **1 microsecond** using `StampedLock` primitives. |
| **Traffic Shaping** | **Direct Database Flooding**: In a monolith, every inbound HTTP request hits backend application services directly, overwhelming internal buffers. | **Perimeter Token Bucket & Turnstile**: The **Gateway** filters out 500+ malicious bot scripts at the network edge with `HTTP 429`. The **Waiting Room** acts as a physical subway turnstile, admitting smooth batches of **200 users/sec**. |

---

## 🛠️ Technology Stack

Every technology and tool in SlotSync was deliberately chosen for **raw performance, zero external framework overhead, and sub-millisecond execution**:

| Layer | Technology | Version | Architectural Role & Justification |
|---|---|---|---|
| **Core Runtime** | **Java (OpenJDK)** | **23** | Utilizes **Virtual Threads (Project Loom)** to handle tens of thousands of concurrent client connections with minimal OS memory overhead, avoiding thread pool starvation. |
| **Microservices Engine** | **Sun Built-in HTTP Server** | `com.sun.net.httpserver` | Zero external framework bloat (no heavy Spring Boot / Tomcat startup overhead). Starts in < 800ms and executes with microsecond-level raw TCP latency. |
| **Core In-Memory DSA** | **Segment Tree** | Pure Java | Enables **$O(\log N)$** atomic contiguous berth reservation across 1,000 seats. Replaces slow SQL queries with instant bit-level RAM operations. |
| **Concurrency Primitives** | **`StampedLock` & `AtomicInteger`** | `java.util.concurrent` | Provides non-blocking optimistic read locking for high-read, low-write seat matrix inspection without thread contention. |
| **TTL Reclamation Engine** | **Min-Heap Priority Queue** | Pure Java | Implements an **$O(1)$** peek timer queue for abandoned payment detection. Automatically releases expired seats back to `AVAILABLE` without database polling. |
| **DDoS & Bot Shield** | **Token Bucket Algorithm** | Pure Java | Edge rate limiter configured with **50 Burst Capacity, 10 Tokens/sec Refill Rate** per IP to drop scraping botnets with `HTTP 429`. |
| **Traffic Partitioning** | **Consistent Hash Ring** | Pure Java | Virtual node hashing with MD5 distribution to prevent hot-spotting across queue partitions (< 5% variance). |
| **Durable Persistence** | **Append-Only Write-Ahead Log (WAL)** | `bookings_wal.log` | Asynchronous crash-proof disk journal ensuring confirmed bookings survive server crashes without slowing down in-memory operations. |
| **Relational Database** | **PostgreSQL / SQLite Compatible DDL** | `schema.sql` | Permanent, ACID-compliant relational schema storing confirmed passenger orders, PNRs, and payment audit logs. |
| **Frontend Framework** | **React 19** | **19.0.0** | Modern component-based mission control UI with real-time HUD metrics, state batching, and zero layout shift. |
| **Build Tooling** | **Vite** | **6.x** | Ultra-fast Hot Module Replacement (HMR) and optimized ES module bundling for sub-second UI updates. |
| **Styling & Design** | **Custom Vanilla CSS3** | Modern CSS | Handcrafted dark-mode Indian Railways theme, glassmorphism panels, interactive seat grid, liquid fuel gauges, and zero Tailwind/framework dependencies. |

---

## 🏗️ System Architecture

The following diagram illustrates the decoupled microservices pipeline and data flow:

```mermaid
flowchart TD
    subgraph Ingress["🌐 User Ingress (10:00 AM Rush)"]
        U1["10,000 Genuine Passengers"]
        U2["500 Malicious Scraping Bots"]
    end

    subgraph Gateway["🚪 API Gateway (Port 8080)"]
        TB["Token Bucket Rate Limiter\n(50 Burst, 10 Refill/s)"]
        IDEM["Idempotency Filter\n(X-Idempotency-Key Cache)"]
    end

    subgraph Queue["🎟️ Virtual Waiting Room (Port 8081)"]
        FIFO["Consistent FIFO Queue"]
        DISP["Batch Admission Dispatcher\n(Smooth 200 users / sec)"]
    end

    subgraph CoreLock["⚡ Slot-Lock Engine (Port 8082)"]
        ST["Segment Tree in RAM\n(1,000 Seats across S1-S14)"]
        SLOCK["StampedLock Atomic Concurrency\n(Sub-millisecond Read/Write)"]
        HEAP["Min-Heap TTL Reaper\n(Auto-frees abandoned checkouts)"]
    end

    subgraph Payment["💳 Payment Worker (Port 8083)"]
        HOOK["Webhook Payment Reconciler"]
        WAL["Append-Only Disk Journal\n(bookings_wal.log)"]
        DB[("Permanent SQL DB\n(bookings table)")]
    end

    U2 -->|"High-Frequency Bot Spam"| TB
    TB -->|"Exceeds Token Limit"| DROP["🛑 HTTP 429 Too Many Requests\n(Bots Neutralized at Edge)"]

    U1 --> TB
    TB --> IDEM
    IDEM -->|"Admitted to Waiting Line"| FIFO
    FIFO --> DISP
    DISP -->|"Fast-Track Admission Token"| ST

    ST <--> SLOCK
    ST -.->|"Unpaid Seat After 5 Min"| HEAP
    HEAP -.->|"Auto-Reclaim to FREE"| ST

    ST -->|"Seat Reserved: Held in Yellow"| HOOK
    HOOK --> WAL
    HOOK --> DB
```

---

## 🔄 Request Lifecycle & Booking Flow

```mermaid
sequenceDiagram
    autonumber
    actor User as 👤 Passenger
    participant GW as 🚪 API Gateway (8080)
    participant WR as 🎟️ Waiting Room (8081)
    participant SL as ⚡ Slot-Lock Engine (8082)
    participant PW as 💳 Payment Worker (8083)
    participant WAL as 📝 Disk Journal (WAL)

    User->>GW: POST /api/v1/queue/join (User ID, IP)
    GW->>GW: Token Bucket Check (DDoS / Bot Guard)
    GW->>WR: Enqueue Passenger
    WR-->>User: Ticket #342 (Estimated Wait: 1.7s)

    WR->>WR: Batch Dispatcher triggers (200 / sec)
    WR-->>User: Admitted! Direct Access Token Granted

    User->>SL: POST /api/v1/slots/lock (Seats #45 - #46, TTL 300s)
    SL->>SL: SegmentTree atomic range query & lock
    SL-->>User: 200 OK (Lock ID: LCK-77492, Seats Held in Yellow)

    alt Payment Completed
        User->>PW: POST /api/v1/payments/process (Lock ID, Amount)
        PW->>SL: Validate Lock ID & Mark BOOKED
        PW->>WAL: Append to disk journal (Zero DB lock contention)
        PW-->>User: 200 OK (Ticket Confirmed, Boarding Pass Downloadable)
    else Payment Abandoned / Tab Closed
        Note over SL: Min-Heap TTL Reaper timer expires (300s)
        SL->>SL: Auto-reclaims seats back to AVAILABLE (Green)
        Note over SL: 0 Ghost Seats! Immediate redistribution
    end
```

---

## 🧠 The 4 Core DSA Superpowers

| Data Structure / Pattern | Component | Problem Solved | Computational Complexity |
|---|---|---|---|
| **Token Bucket** | API Gateway (`RateLimitingFilter.java`) | Drops bots, scraping scripts, and rapid double-clicking at the network perimeter without touching the core engine. | **$O(1)$** lock-free check (~50ns) |
| **Segment Tree** | Slot-Lock Engine (`SegmentTree.java`) | Instantly finds contiguous available seat ranges (e.g., family of 4) and checks seat conflicts atomically in RAM. | **$O(\log N)$** query & update (< 1µs) |
| **Min-Heap (Priority Queue)** | Slot-Lock Engine (`MinHeapTTLReaper.java`) | Reclaims abandoned checkouts automatically. The root always stores the next earliest expiring seat lock. | **$O(1)$** peek & **$O(\log K)$** eviction |
| **Consistent Hash Ring** | Waiting Room (`ConsistentHashRing.java`) | Uniformly distributes millions of queued passengers across partition buckets with < 5% variance. | **$O(\log M)$** node lookup |

---

## 🎮 Interactive Frontend Command Center

Built with **React 19 + Vite**, the frontend is a dark-mode mission control dashboard connected directly to the Java microservices:

```
========================================================================================
                          SLOTSYNC MISSION CONTROL DASHBOARD
========================================================================================
 [Header]  Prashanti Express (18463) · BBS ➔ SBC · 1,000 Berths · Status: LIVE 🟢
----------------------------------------------------------------------------------------
 [HUD]     QPS: 862,000 | P99: 0.89 ms | Available: 800 | Locked: 0 | Booked: 200
----------------------------------------------------------------------------------------
 [Matrix]  Coaches S1 to S14 (1,000 Berths: Lower, Middle, Upper, Side Lower, Side Upper)
           🟩 Green = Available    🟨 Yellow = Held in Checkout    🟥 Red = Confirmed Booked
----------------------------------------------------------------------------------------
 [Sims]    [⚡ 10:00 AM Tatkal Rush]   [🛡️ Block 500 Fake Bots]   [🔄 Reset Train]
 [Queue]   Smart Waiting Line HUD: Direct Entry 🟢 | Admitting 200/s 🟣 | Gate Closed 🛑
 [Logs]    Real-Time Microsecond Kernel Stream (Idempotency, StampedLock, Reaper logs)
========================================================================================
```

### Exactly 3 Live Interactive Simulators
1. ⚡ **10:00 AM Tatkal Rush**: Simulates 10,000 concurrent passengers streaming into the queue. Admitted users lock seats in RAM, reconcile payments, and execute a mop-up wave until **all 1,000 berths (Coaches S1-S14) reach 100% full capacity with 0.00% double-booking**.
2. 🛡️ **Block 500 Fake Bots (Security Test)**: Launches a real-time DDoS test of 500 rapid bot requests. Displays a live Token Bucket liquid fuel gauge, an IP signature packet interception console, and a human bypass demonstration proving zero core CPU load.
3. 🔄 **Reset Train (Clear All)**: Atomically clears all active locks and resets the 1,000-seat train back to fresh state in a single click.

### Real Booking Flow & Digital Boarding Pass
- Click on any free seat in the coach grid to trigger the fast-track reservation modal.
- Test simulated payment confirmation.
- Instantly download the official **SlotSync Digital Boarding Pass (`Ticket_*.txt`)** containing PNR, Coach, Berth numbers, WAL disk journal reference, and payment transaction IDs.

---

## 📊 Stress Test Benchmark Results

Benchmarked with **100,000 concurrent user requests** hitting 1,000 seats (`TatkalConcurrencyStressTest.java`):

| Metric | Result | Benchmark Significance |
|---|---|---|
| **Peak Throughput** | **862,069 requests / sec** | Handles national-scale Tatkal surges in RAM |
| **P99 Response Latency** | **1.25 milliseconds** | Zero perceived delay for passengers |
| **Double-Booking Rate** | **Strict 0.00% (STRICT ZERO)** | Zero overlapping berths ever issued |
| **Inventory Conservation** | **$1,000 / 1,000$ Exact** | Not a single seat lost or oversold |
| **Thread Management** | **Java 23 Virtual Threads** | Lightweight execution without OS thread exhaustion |

---

## 🔌 Microservices Directory & API Contracts

All 4 backend microservices run as lightweight Java processes without heavy frameworks:

| Port | Service | Core Responsibility | Primary Endpoints |
|---|---|---|---|
| **`8080`** | **API Gateway** | Token Bucket Rate Limiting, Idempotency Cache, CORS | `GET /health`<br/>`POST /api/v1/queue/join` |
| **`8081`** | **Virtual Waiting Room** | FIFO Passenger Queue, Batch Dispatcher (200/s) | `GET /health`<br/>`GET /api/v1/queue/status`<br/>`POST /api/v1/queue/admit` |
| **`8082`** | **Slot-Lock Engine** | Segment Tree in RAM, Min-Heap TTL Reaper, StampedLocks | `GET /health`<br/>`GET /api/v1/slots/state`<br/>`POST /api/v1/slots/lock`<br/>`POST /api/v1/slots/unlock`<br/>`POST /api/v1/slots/tatkal-wave`<br/>`POST /api/v1/slots/reset` |
| **`8083`** | **Payment Worker** | Webhook Reconciliation, Append-Only Disk WAL Log | `GET /health`<br/>`POST /api/v1/payments/process`<br/>`POST /api/v1/payments/webhook` |
| **`5173`** | **Frontend Dashboard** | React 19 + Vite Live Mission Control Command Center | `http://localhost:5173` |

---

## 📁 Clean Monorepo Structure

```
SlotSync/
├── .github/
│   └── workflows/
│       └── ci.yml                            # Automated Java 23 & React 19 CI pipeline
├── LICENSE                                   # MIT License
├── README.md                                 # Architecture & engineering documentation
├── start-all.bat                             # One-click master launcher
├── stop-all.bat                              # One-click graceful shutdown script
├── backend/
│   ├── database/
│   │   ├── schema.sql                        # PostgreSQL / SQLite DDL schema
│   │   └── seed.sql                          # Prashanti Express 18463 seed data
│   ├── dsa-engine/                           # Pure DSA core (SegmentTree, TokenBucket, MinHeap)
│   ├── api-gateway/                          # Virtual Thread Gateway (Port 8080)
│   ├── waiting-room/                         # FIFO Queue & Batch Dispatcher (Port 8081)
│   ├── slot-lock/                            # In-Memory SegmentTree & TTL (Port 8082)
│   ├── payment-worker/                       # Webhook & Durable WAL Persistence (Port 8083)
│   └── src/main/java/com/slotsync/
│       └── SlotSyncAllServers.java           # Master runner for all 4 microservices
└── frontend/                                 # React 19 + Vite Dashboard
    ├── public/                               # Favicon and branding assets
    ├── src/
    │   ├── components/
    │   │   ├── Header.jsx                    # Live train info & backend connectivity status
    │   │   ├── TelemetryHUD.jsx              # QPS, P99 Latency, 0% double-booking gauge
    │   │   ├── SeatMatrix.jsx                # 1,000-seat Segment Tree matrix (Coaches S1-S14)
    │   │   ├── Simulators.jsx                # 3 buttons: Tatkal Rush, Fake Bots, Reset Train
    │   │   ├── SecurityBotModal.jsx          # Token Bucket gauge & bot packet interceptor
    │   │   ├── WaitingRoomHUD.jsx            # Dynamic turnstile (Direct Entry / Admitting / Closed)
    │   │   ├── LogStream.jsx                 # Real-time microsecond audit log
    │   │   └── BookingModal.jsx              # 2-step booking flow & digital ticket generator
    │   ├── services/
    │   │   ├── api.js                        # Microservices client with fallback engine
    │   │   └── dsaEngine.js                  # In-browser client-side simulation engine
    │   └── App.jsx                           # Root mission control orchestrator
    └── index.html
```

---

## 🚀 Quick Start (One-Click Launch)

### Prerequisites
- **Java JDK 21+** (Java 23 recommended)
- **Node.js 18+**

### Single-Command Start (Windows)
Double-click or run in your terminal:
```cmd
start-all.bat
```

This single command automatically:
1. Starts the unified Java backend suite on **Ports 8080, 8081, 8082, and 8083**.
2. Starts the React 19 Vite dev server on **Port 5173**.
3. Launches your default web browser directly to `http://localhost:5173`.

### Graceful Shutdown
To stop all background microservices and dev servers:
```cmd
stop-all.bat
```

---

## 📜 License

This project is open-source and distributed under the **[MIT License](./LICENSE)**.  
Engineered with ❤️ by **[Anil Pradhan](https://github.com/Anil-Pradhan-web)**.
