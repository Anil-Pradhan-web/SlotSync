# SlotSync — Step-by-Step Implementation Roadmap

Building SlotSync is broken down into **6 simple, easy-to-follow steps (Milestones)**. We start from the pure algorithmic core and build upwards to the full microservice system.

---

## 🛠️ Milestone 1: Build the 4 Core DSA Tools (Java)

**Goal**: Build and test the 4 brain algorithms in pure Java before adding any web servers.

- [ ] **Step 1.1**: Setup the Java project with Maven (Java 21).
- [ ] **Step 1.2**: Write `TokenBucket.java` (The bot-stopping guard).
  - *Test*: Write a test simulating 50 clicks in 1 second; verify only 10 pass and 40 are blocked.
- [ ] **Step 1.3**: Write `SegmentTree.java` (The super-fast seat checker).
  - *Test*: Mark Seats 15 to 18 as held; verify checking range [15, 18] returns "Occupied" in under 1 microsecond.
- [ ] **Step 1.4**: Write `MinHeapTTLReaper.java` (The 5-minute auto-expiry alarm clock).
  - *Test*: Create a hold with a 2-second test timer; verify the seat automatically frees itself after 2 seconds.
- [ ] **Step 1.5**: Write `ConsistentHashRing.java` (The fair work distributor).
  - *Test*: Route 1,000 user IDs; verify each server gets an almost equal share of users.

👉 **Checkpoint 1**: Run `mvn test` — All 4 algorithms pass 100% of tests.

---

## 🛡️ Milestone 2: Worker 1 — The Security Guard (Gateway Service)

**Goal**: Create the front entrance service that stops bots and lets only real humans through.

- [ ] **Step 2.1**: Create a lightweight Spring Boot Gateway application.
- [ ] **Step 2.2**: Attach the `TokenBucket` filter to every incoming request.
- [ ] **Step 2.3**: If someone clicks too fast, return HTTP 429 (`"Too Many Requests, please slow down!"`).
- [ ] **Step 2.4**: Check `Idempotency-Key` so accidental double-clicks don't cause duplicate charges.

👉 **Checkpoint 2**: Fire a bot script with 100 fast clicks; see the gateway block 90% of them smoothly.

---

## 🎟️ Milestone 3: Worker 2 — The Line Manager (Virtual Waiting Room)

**Goal**: Put 100,000 users in an orderly digital line and release them in controlled batches of 200 users/second.

- [ ] **Step 3.1**: Create the Waiting Room service endpoint: `POST /api/queue/join`.
- [ ] **Step 3.2**: Hand the user a digital token: *"You are #500 in line"*.
- [ ] **Step 3.3**: Write a background dispatcher that admits exactly **200 users every second** to proceed to the seat selection page.

👉 **Checkpoint 3**: Simulate 5,000 users joining at once; watch the queue steadily feed 200 users/second into the store.

---

## 🪑 Milestone 4: Worker 3 — The Seat Counter (Order & Locking Service)

**Goal**: Check seats in RAM and hold them for 5 minutes without touching any slow database.

- [ ] **Step 4.1**: Create the Order Locking service with `SegmentTree` embedded in RAM.
- [ ] **Step 4.2**: Endpoint `POST /api/slots/lock` (User requests Seats 10 to 14).
- [ ] **Step 4.3**: Check availability in Segment Tree:
  - If free $\rightarrow$ Mark held and start 5-minute timer in Min-Heap.
  - If taken $\rightarrow$ Instantly reply: *"Seats unavailable, please pick other seats."*
- [ ] **Step 4.4**: Test ghost inventory clean-up: Abandon a payment and verify the seat returns to `FREE` automatically.

👉 **Checkpoint 4**: Verify 0% chance of double-booking when multiple threads request overlapping seats.

---

## 💳 Milestone 5: Worker 4 — The Bank Clerk (Payment & Database Worker)

**Goal**: Receive payment confirmation and permanently save the ticket to PostgreSQL.

- [ ] **Step 5.1**: Endpoint `POST /api/payments/webhook` to catch bank payment success.
- [ ] **Step 5.2**: Cancel the 5-minute auto-expiry timer (the user has paid!).
- [ ] **Step 5.3**: Calmly save the booking in the PostgreSQL database.
- [ ] **Step 5.4**: Generate the digital ticket with a booking reference ID.

👉 **Checkpoint 5**: Complete a full end-to-end booking: Enter Line $\rightarrow$ Lock Seats $\rightarrow$ Pay $\rightarrow$ Get Confirmed Ticket.

---

## 💥 Milestone 6: The 100,000 Click Flash-Sale Stress Test!

**Goal**: Prove mathematically that SlotSync will not crash and will never double-book under peak Tatkal rush.

- [ ] **Step 6.1**: Write a test script that spawns **100,000 simulated users** all clicking at the exact same second for 1,000 seats.
- [ ] **Step 6.2**: **Verify Consistency**:
  $$\text{Confirmed Bookings} + \text{Remaining Free Seats} \equiv 1,000$$
  - Exactly 1,000 tickets accounted for.
  - Exactly **ZERO double-bookings**.
- [ ] **Step 6.3**: Measure latency: Verify that checking seats stayed under **15 milliseconds** during the entire sale.

---

## Summary Checklist

| Milestone | Component | What It Delivers |
|---|---|---|
| **M1** | Pure Java DSA | Token Bucket, Segment Tree, Min-Heap, Consistent Hashing |
| **M2** | Gateway | Stops bots and spam-clickers at the door |
| **M3** | Waiting Room | Lets in 200 people/second in a calm, orderly queue |
| **M4** | Seat Counter | 1-microsecond seat checking & 5-minute auto-expiry |
| **M5** | Bank Clerk | Saves paid tickets to PostgreSQL calmly |
| **M6** | Stress Test | 100k requests test proving 0 crashes and 0 double-bookings |
