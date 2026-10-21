# SlotSync — Product Requirements Document (PRD)

## 1. What is SlotSync?
**SlotSync** is a smart ticket and slot booking engine designed for crazy flash sales (like IRCTC Tatkal train bookings, concert ticket drops, or flash electronics sales).

Traditional booking websites crash because thousands of people rush into the database at the exact same second. SlotSync stops the crash by **organizing users in a virtual line outside, locking seats in ultra-fast computer memory (RAM), and only touching the database when payment is actually done.**

---

## 2. The Real-Life Story: "The 10:00 AM Tatkal Disaster"

To understand why traditional systems fail, imagine this real-life scenario:

It is **Friday morning at 9:59 AM**, right before a major holiday.
- A popular train has only **1,000 seats** available.
- Behind their laptops and phones, **100,000 hopeful buyers** are watching the clock tick down: `09:59:58... 09:59:59...`
- At **10:00:00 AM sharp**, all 100,000 people hit **"Book Now"** at the exact same second.

Here is how a traditional website tragically breaks down into **3 major disasters**:

```
 100,000 People Click at 10:00:00 AM
                  │
                  ▼
┌────────────────────────────────────────────────────────┐
│              The Traditional Website                   │
│                                                        │
│  [Disaster 1: The Crash]                               │
│  100,000 people rush into a small database that only   │
│  has 100 open doors. The server freezes and crashes.   │
│                                                        │
│  [Disaster 2: Double-Booking]                          │
│  Rahul and Priya both click Seat #45. Both get charged │
│  and both are given the exact same seat!               │
│                                                        │
│  [Disaster 3: Ghost Inventory]                         │
│  Amit locks Seat #12, but closes his tab at checkout.  │
│  Seat #12 stays locked forever. Genuine buyers get     │
│  turned away even though the seat was never bought!    │
└────────────────────────────────────────────────────────┘
```

### Disaster 1: The Crash (Database Exhaustion)
Think of a small store with one cashier. If 100,000 customers try to push through the glass door at the exact same instant, the glass breaks and nobody gets served. In software, the database only has about 100 connections available. When 100,000 requests try to connect at once, the database runs out of memory, CPU hits 100%, and users see an ugly error: **"504 Gateway Timeout / Server Busy"**.

### Disaster 2: Double-Booking (The Race Condition)
Meet **Rahul** and **Priya**. Both of them want **Seat #45**.
1. At microsecond 1, the server checks the database for Rahul: *"Is Seat #45 free?"* The database replies: *"Yes, go ahead."*
2. At microsecond 2 (before Rahul finishes writing his booking), Priya's click arrives. The database replies to Priya: *"Yes, Seat #45 is still free!"*
3. Both proceed to pay. Both are charged money. Both are assigned Seat #45. Now customer support has an angry disaster on their hands.

### Disaster 3: Ghost Inventory (The Phantom Hold)
Meet **Amit**. Amit successfully clicks **Seat #12** and reaches the payment page. The system holds the seat for him.
- While looking for his debit card, Amit's phone battery dies, or he changes his mind and closes the browser tab.
- The system keeps waiting and waiting for Amit's payment.
- Other genuine buyers see a message: *"All seats are sold out!"*
- Nobody bought Seat #12, but nobody else can buy it either. The seat is stuck like a "Ghost".

---

## 3. How SlotSync Fixes This (The Solution)

Instead of letting 100,000 people attack the database at once, SlotSync handles the sale like a well-organized event:

1. **A Security Guard at the Door (Token Bucket)**:
   - Blocks automated bots and scripts that click 50 times a second.
   - Lets only genuine human clicks through.
2. **A Virtual Waiting Room (Orderly Line)**:
   - Gives every user a digital token: *"You are #450 in line. Estimated wait: 2 seconds."*
   - Lets users into the booking room in smooth, controlled batches of **200 people per second**.
3. **Checking Seats in Super-Fast Memory (Segment Tree)**:
   - Instead of asking a slow database, seat availability is kept in super-fast computer RAM.
   - Checking whether 4 seats together (e.g., Seats 15 to 18) are free takes less than 1 microsecond.
4. **The 5-Minute Auto-Release Timer (Min-Heap with TTL)**:
   - When Amit reaches the payment page, a 5-minute countdown starts in memory.
   - If Amit closes his tab or fails payment, the timer instantly frees Seat #12 the exact second the 5 minutes are up.
   - Seat #12 goes straight to the next waiting buyer. **Zero Ghost Inventory!**
5. **Calm Database Saving (Payment Worker)**:
   - The database is only touched when money has actually been paid.
   - Because only 200 people enter per second, the database is always calm, happy, and never crashes.

---

## 4. Step-by-Step User Journey

```
User (Rahul)                SlotSync System                      Result
    │                              │                                │
    ├──── 1. Hits "Book Now" ─────>│ Check Rate Limits (Guard)      │ Allowed
    │                              │                                │
    │<─── 2. "You are #45 in line" ┤ Put in Virtual Waiting Room    │ Smooth line
    │                              │                                │
    │<─── 3. "It is your turn!" ───┤ Batch Dispatcher (200/sec)     │ Enter store
    │                              │                                │
    ├──── 4. Select Seats 15 to 18>│ Segment Tree checks in RAM     │ Held for 5 mins
    │                              │                                │
    │<─── 5. Payment Page (5m TTL)─┤ Min-Heap Timer starts (300s)   │ No ghost seats
    │                              │                                │
    ├──── 6. Completes Payment ───>│ Worker saves ticket in DB      │ Confirmed!
    │                              │                                │
    │<─── 7. Ticket Confirmed! ────┤ Send confirmation & PDF ticket │ Happy user
```

---

## 5. Simple Project Goals (Our Targets)

| Goal | Target Number | Why It Matters |
|---|---|---|
| **Traffic Handling at the Gate** | 100,000 requests/second | The site must stay alive even when 100k people click at 10:00:00 AM. |
| **Controlled Booking Rate** | 200 users/second | The database only sees 200 calm requests per second, so it never overheats. |
| **Seat Availability Check Speed** | Under 15 milliseconds | Searching for free seats feels instant to the user. |
| **Over-booking Rate** | **0.00% (Strict Zero)** | Two people must NEVER get the same seat under any circumstance. |
| **Ghost Seat Recovery Time** | Under 1 second after 5-min timeout | If a buyer drops off, their seat is back in the market immediately. |
