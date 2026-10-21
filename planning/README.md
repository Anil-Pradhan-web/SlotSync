# SlotSync — Planning & Architecture Hub

Welcome to the planning documents for **SlotSync**! 

SlotSync is built to solve the **"10:00 AM Tatkal Rush / Flash-Sale Crash"**, where 100,000 people click at the same second for only 1,000 seats.

---

## 📚 What's Inside the Planning Folder?

All documents are written in **simple, friendly English** with everyday analogies so anyone can easily understand how the system works:

| # | Document | What It Covers |
|---|---|---|
| **01** | [The Story & Requirements](file:///c:/Users/ANIL/Desktop/SlotSync/planning/01-PRD-and-Requirements.md) | **The Real-Life Story**: Rahul, Priya, and Amit during Tatkal rush. The 3 disasters (The Crash, Double-Booking, and Ghost Inventory) and how SlotSync fixes them. |
| **02** | [High-Level Architecture](file:///c:/Users/ANIL/Desktop/SlotSync/planning/02-System-Architecture-HLD.md) | **The 4 Workers**: The Security Guard (Gateway), The Line Manager (Waiting Room), The Seat Counter (Locking Engine), and The Bank Clerk (Payment Worker). |
| **03** | [The 4 Core DSA Tools](file:///c:/Users/ANIL/Desktop/SlotSync/planning/03-DSA-Core-Engine-LLD.md) | **The Brain Algorithms**: Token Bucket (Rate limit), Segment Tree (Seat checking), Min-Heap (5-min auto-release timer), and Consistent Hashing (Fair load balancing). |
| **04** | [Seat Life Cycle & Edge Cases](file:///c:/Users/ANIL/Desktop/SlotSync/planning/04-State-Machine-and-Failure-Modes.md) | **The Life of a Seat**: How a seat moves from `FREE` $\rightarrow$ `HELD` $\rightarrow$ `BOOKED`, and how we handle impatient double-clicks and abandoned tabs. |
| **05** | [Step-by-Step Roadmap](file:///c:/Users/ANIL/Desktop/SlotSync/planning/05-Implementation-Roadmap.md) | **The Build Plan**: 6 clear milestones starting from pure Java algorithms up to the 100,000-click stress test. |

---

## 🎯 How We Will Build It
1. **Milestone 1**: Write and test the 4 pure Java algorithms (`TokenBucket`, `SegmentTree`, `MinHeapTimer`, `ConsistentHashRing`).
2. **Milestone 2**: Build Worker 1 (The Security Guard Gateway).
3. **Milestone 3**: Build Worker 2 (The Waiting Room Line Manager).
4. **Milestone 4**: Build Worker 3 (The RAM Seat Counter).
5. **Milestone 5**: Build Worker 4 (The Database Bank Clerk).
6. **Milestone 6**: Run the 100k-click stress test to prove 0 crashes and 0 double-bookings!
