# SlotSync

> **Ultra-High Concurrency Ticket & Slot Reservation Engine**  
> Engineered to eliminate race conditions, database deadlocks, and ghost inventory during peak flash sales and Tatkal ticket rushes.

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
 [ Gateway Service ] ────────────> Token Bucket Algorithm (Stops bots & drops spam clicks)
          │
          ▼
 [ Virtual Waiting Room ] ───────> Consistent Hashing & Queue (Releases smooth batches: 200 users/sec)
          │
          ▼
 [ Order & Locking Engine ] ─────> Segment Tree (O(log N) seat checking in RAM)
          │                        Min-Heap with TTL (5-min auto-release timer for ghost seats)
          ▼
 [ Payment & Event Worker ] ─────> Asynchronous DB persistence (Zero SQL lock contention)
          │
          ▼
 [ PostgreSQL Database ] ────────> Calm, durable source of truth
```

---

## 🧠 The 4 Core DSA Pillars

| DSA / Pattern | Problem Solved | Speed / Complexity |
|---|---|---|
| **Token Bucket** | Stops bots and spam-clickers at the door | $O(1)$ lock-free check (~50ns) |
| **Segment Tree** | Instant contiguous seat availability & lock in RAM | $O(\log N)$ range query (< 1µs) |
| **Min-Heap (Priority Queue) with TTL** | 5-minute auto-expiry of abandoned checkouts | $O(1)$ peek — Zero database polling |
| **Consistent Hashing Ring** | Equal partition balancing across waiting room nodes | Uniform distribution (< 5% variance) |

---

## 📚 Project Specifications & Architecture Docs

Detailed blueprints and architectural designs are available in the [`planning/`](planning/README.md) directory:

- 📖 [`01-PRD-and-Requirements.md`](planning/01-PRD-and-Requirements.md) — The complete Tatkal story, user flows, and project targets.
- 🏗️ [`02-System-Architecture-HLD.md`](planning/02-System-Architecture-HLD.md) — Microservices topology, the 4 workers, and request flows.
- 🧠 [`03-DSA-Core-Engine-LLD.md`](planning/03-DSA-Core-Engine-LLD.md) — In-depth DSA explanations with Java code implementations.
- 🔄 [`04-State-Machine-and-Failure-Modes.md`](planning/04-State-Machine-and-Failure-Modes.md) — Seat life cycles, idempotency keys, and edge-case mitigations.
- 🗺️ [`05-Implementation-Roadmap.md`](planning/05-Implementation-Roadmap.md) — 6-phase task breakdown from pure Java DSA to 100k stress tests.

---

## 🛠️ Tech Stack
- **Language**: Java 21
- **Framework**: Spring Boot 3.x (Spring Cloud Gateway, WebFlux)
- **Data Structures**: Token Bucket, Segment Tree, Min-Heap, Consistent Hash Ring
- **Data & Caching**: PostgreSQL, Redis
- **Build Tool**: Apache Maven
