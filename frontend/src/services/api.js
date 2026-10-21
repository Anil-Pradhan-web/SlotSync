/**
 * SlotSync - Production Backend API Service
 * Connects directly to Java microservices (127.0.0.1) with seamless local fallback.
 */

import { SegmentTreeSim, MinHeapTTLSim, TokenBucketSim } from './dsaEngine';

export class SlotSyncAPI {
  constructor(onGhostReaped) {
    this.gatewayUrl = 'http://127.0.0.1:8080';
    this.waitingRoomUrl = 'http://127.0.0.1:8081';
    this.slotLockUrl = 'http://127.0.0.1:8082';
    this.paymentUrl = 'http://127.0.0.1:8083';

    this.isBackendLive = false;
    this.cachedBackendState = null;

    // Local in-memory DSA engine fallback
    this.simTree = new SegmentTreeSim(1000);
    this.simBucket = new TokenBucketSim(50, 10);
    this.localLocks = new Map();
    this.localBookings = new Map();
    this.onGhostReaped = onGhostReaped;

    this.reaper = new MinHeapTTLSim((expired) => {
      if (this.localLocks.has(expired.lockId)) {
        this.simTree.unlock(expired.startSeat, expired.endSeat);
        this.localLocks.delete(expired.lockId);
        if (this.onGhostReaped) {
          this.onGhostReaped(expired);
        }
      }
    });

    // Seed initial 3 bookings for Rahul, Priya, Amit
    this.localBookings.set('ORD-TATKAL-001', { startSeat: 0, endSeat: 1, userId: 'USR-RAHUL-SHARMA' });
    this.localBookings.set('ORD-TATKAL-002', { startSeat: 2, endSeat: 3, userId: 'USR-PRIYA-VERMA' });
    this.localBookings.set('ORD-TATKAL-003', { startSeat: 4, endSeat: 5, userId: 'USR-AMIT-PATEL' });
    this.simTree.updateRange(1, 0, 999, 0, 5, 1);
  }

  destroy() {
    if (this.reaper) this.reaper.destroy();
  }

  async checkHealth() {
    try {
      const res = await fetch(`${this.gatewayUrl}/health`, { signal: AbortSignal.timeout(600) });
      this.isBackendLive = res.ok;
    } catch {
      this.isBackendLive = false;
    }
    return this.isBackendLive;
  }

  async fetchLiveState() {
    try {
      const res = await fetch(`${this.slotLockUrl}/api/v1/slots/state`, { signal: AbortSignal.timeout(800) });
      if (res.ok) {
        this.cachedBackendState = await res.json();
        this.isBackendLive = true;
        return this.cachedBackendState;
      }
    } catch {
      this.isBackendLive = false;
    }

    // Fallback to local state if backend is offline
    const activeLocks = Array.from(this.localLocks.values());
    return {
      totalSeats: 1000,
      availableSeats: this.simTree.getTotalAvailable(),
      occupiedSeats: 1000 - this.simTree.getTotalAvailable(),
      ghostSaved: 0,
      activeLocks,
      seedBooked: [[0, 1], [2, 3], [4, 5]]
    };
  }

  async joinQueue(userId = 'USR-PASSENGER-ME') {
    if (this.isBackendLive) {
      try {
        const res = await fetch(`${this.gatewayUrl}/api/v1/queue/join`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-User-Id': userId,
            'X-Idempotency-Key': 'IDEM_' + userId + '_' + Date.now()
          },
          body: JSON.stringify({ userId })
        });
        const data = await res.json();
        return { ok: res.ok, status: res.status, data };
      } catch (e) {
        console.warn('Backend queue join failed, falling back:', e);
      }
    }

    // Local fallback: Token bucket rate limiter check
    if (!this.simBucket.tryAcquire(1)) {
      return { ok: false, status: 429, data: { error: { code: 'RATE_LIMIT_EXCEEDED', message: 'Too Many Requests' } } };
    }
    const ticketId = 'TKT_' + Math.random().toString(36).substring(2, 8).toUpperCase();
    return { ok: true, status: 200, data: { queueTicketId: ticketId, status: 'QUEUED' } };
  }

  async lockSlots(userId, startSeat, endSeat, ttlMs = 300000) {
    if (this.isBackendLive) {
      try {
        const res = await fetch(`${this.slotLockUrl}/api/v1/slots/lock?start=${startSeat}&end=${endSeat}&ttl=${ttlMs}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-User-Id': userId },
          body: JSON.stringify({ userId, startSeat, endSeat, ttlMs })
        });
        const data = await res.json();
        return { ok: res.ok, status: res.status, data };
      } catch (e) {
        console.warn('Backend lock failed, falling back:', e);
      }
    }

    // Local fallback: SegmentTree lock
    const locked = this.simTree.lock(startSeat, endSeat);
    if (!locked) {
      return { ok: false, status: 409, data: { error: { code: 'SLOTS_UNAVAILABLE' } } };
    }

    const lockId = 'LCK_' + Math.random().toString(36).substring(2, 9).toUpperCase();
    const expiryTime = Date.now() + ttlMs;
    const node = { lockId, userId, startSeat, endSeat, expiryTime };
    this.localLocks.set(lockId, node);
    this.reaper.push(node);

    return { ok: true, status: 200, data: { lockId, start: startSeat, end: endSeat, expiresAt: expiryTime } };
  }

  async payWebhook(lockId, userId, startSeat, endSeat, amount, txRef) {
    if (this.isBackendLive) {
      try {
        const res = await fetch(`${this.paymentUrl}/api/v1/payments/webhook`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ lockId, userId, startSeat, endSeat, amount, transactionRef: txRef })
        });
        const data = await res.json();
        return { ok: res.ok, status: res.status, data };
      } catch (e) {
        console.warn('Backend payment failed, falling back:', e);
      }
    }

    // Local fallback: Confirm lock & persist
    this.reaper.remove(lockId);
    this.localLocks.delete(lockId);
    const orderId = 'ORD_' + Math.random().toString(36).substring(2, 9).toUpperCase();
    const booking = {
      bookingId: 'BKG_' + Math.random().toString(36).substring(2, 9).toUpperCase(),
      orderId,
      lockId,
      userId,
      start: startSeat,
      end: endSeat,
      amount,
      transactionRef: txRef,
      status: 'CONFIRMED'
    };
    this.localBookings.set(orderId, booking);
    return { ok: true, status: 200, data: booking };
  }

  buildSeatMatrix(backendState) {
    const states = [];
    const activeLocks = backendState?.activeLocks || Array.from(this.localLocks.values());
    const seedBooked = backendState?.seedBooked || [[0, 1], [2, 3], [4, 5]];

    for (let i = 0; i < 1000; i++) {
      const coachIndex = Math.floor(i / 72) + 1;
      const coachName = 'S' + coachIndex;
      const seatInCoach = (i % 72) + 1;
      const berthType = this.getBerthType(seatInCoach);

      let status = 'AVAILABLE';
      let lockInfo = null;

      // Check booked
      for (const range of seedBooked) {
        if (i >= range[0] && i <= range[1]) {
          status = 'BOOKED';
          break;
        }
      }

      // Check locked
      if (status !== 'BOOKED') {
        for (const lock of activeLocks) {
          if (i >= lock.startSeat && i <= lock.endSeat) {
            status = 'LOCKED';
            lockInfo = lock;
            break;
          }
        }
      }

      states.push({
        seatNumber: i,
        displayNumber: i + 1,
        coach: coachName,
        seatInCoach,
        berthType,
        status,
        lockInfo,
        price: 1450.00
      });
    }
    return states;
  }

  getBerthType(num) {
    const mod = num % 8;
    if (mod === 1 || mod === 4) return 'Lower';
    if (mod === 2 || mod === 5) return 'Middle';
    if (mod === 3 || mod === 6) return 'Upper';
    if (mod === 7) return 'Side Lower';
    return 'Side Upper';
  }
}
