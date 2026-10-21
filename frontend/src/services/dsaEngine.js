/**
 * SlotSync - Client-Side High-Fidelity DSA Simulation Engine
 * Replicates SegmentTree, MinHeapTTL, and TokenBucket in pure JS.
 */

export class SegmentTreeSim {
  constructor(size = 1000) {
    this.size = size;
    this.tree = new Int32Array(4 * size);
    this.lazy = new Int8Array(4 * size).fill(-1);
    this.build(1, 0, size - 1);
  }

  build(node, start, end) {
    if (start === end) {
      this.tree[node] = 1;
      return;
    }
    const mid = (start + end) >> 1;
    this.build(2 * node, start, mid);
    this.build(2 * node + 1, mid + 1, end);
    this.tree[node] = this.tree[2 * node] + this.tree[2 * node + 1];
  }

  pushDown(node, start, end) {
    if (this.lazy[node] === -1 || start === end) return;
    const mid = (start + end) >> 1;
    const val = this.lazy[node];

    this.lazy[2 * node] = val;
    this.tree[2 * node] = val === 1 ? 0 : (mid - start + 1);

    this.lazy[2 * node + 1] = val;
    this.tree[2 * node + 1] = val === 1 ? 0 : (end - mid);

    this.lazy[node] = -1;
  }

  queryRange(node, start, end, l, r) {
    if (r < start || end < l) return 0;
    if (l <= start && end <= r) return this.tree[node];
    this.pushDown(node, start, end);
    const mid = (start + end) >> 1;
    return this.queryRange(2 * node, start, mid, l, r) +
           this.queryRange(2 * node + 1, mid + 1, end, l, r);
  }

  updateRange(node, start, end, l, r, state) {
    if (r < start || end < l) return;
    if (l <= start && end <= r) {
      this.lazy[node] = state;
      this.tree[node] = state === 1 ? 0 : (end - start + 1);
      return;
    }
    this.pushDown(node, start, end);
    const mid = (start + end) >> 1;
    this.updateRange(2 * node, start, mid, l, r, state);
    this.updateRange(2 * node + 1, mid + 1, end, l, r, state);
    this.tree[node] = this.tree[2 * node] + this.tree[2 * node + 1];
  }

  query(l, r) {
    return this.queryRange(1, 0, this.size - 1, l, r);
  }

  lock(l, r) {
    const needed = r - l + 1;
    const available = this.query(l, r);
    if (available < needed) return false;
    this.updateRange(1, 0, this.size - 1, l, r, 1);
    return true;
  }

  unlock(l, r) {
    this.updateRange(1, 0, this.size - 1, l, r, 0);
  }

  getTotalAvailable() {
    return this.tree[1];
  }

  findAvailableBatches(targetSeats = 72, maxBatchSize = 4) {
    const batches = [];
    let accumulated = 0;
    let start = -1;

    for (let i = 0; i < this.size && accumulated < targetSeats; i++) {
      const isAvail = this.query(i, i) === 1;
      if (isAvail) {
        if (start === -1) start = i;
        const len = i - start + 1;
        if (len === maxBatchSize || i === this.size - 1 || this.query(Math.min(this.size - 1, i + 1), Math.min(this.size - 1, i + 1)) === 0) {
          batches.push({ start, end: i, seats: len });
          accumulated += len;
          start = -1;
        }
      } else {
        if (start !== -1) {
          batches.push({ start, end: i - 1, seats: i - start });
          accumulated += (i - start);
          start = -1;
        }
      }
    }
    return batches;
  }
}

export class MinHeapTTLSim {
  constructor(onExpire) {
    this.heap = [];
    this.onExpire = onExpire;
    this.timer = setInterval(() => this.reap(), 1000);
  }

  destroy() {
    if (this.timer) clearInterval(this.timer);
  }

  push(lockNode) {
    this.heap.push(lockNode);
    this.bubbleUp(this.heap.length - 1);
  }

  remove(lockId) {
    const idx = this.heap.findIndex(n => n.lockId === lockId);
    if (idx !== -1) {
      this.heap.splice(idx, 1);
      this.rebuild();
      return true;
    }
    return false;
  }

  reap() {
    const now = Date.now();
    while (this.heap.length > 0 && this.heap[0].expiryTime <= now) {
      const expired = this.heap.shift();
      this.bubbleDown(0);
      if (this.onExpire) {
        this.onExpire(expired);
      }
    }
  }

  bubbleUp(idx) {
    while (idx > 0) {
      const parent = (idx - 1) >> 1;
      if (this.heap[idx].expiryTime < this.heap[parent].expiryTime) {
        [this.heap[idx], this.heap[parent]] = [this.heap[parent], this.heap[idx]];
        idx = parent;
      } else break;
    }
  }

  bubbleDown(idx) {
    const len = this.heap.length;
    while (true) {
      let left = 2 * idx + 1;
      let right = 2 * idx + 2;
      let smallest = idx;
      if (left < len && this.heap[left].expiryTime < this.heap[smallest].expiryTime) smallest = left;
      if (right < len && this.heap[right].expiryTime < this.heap[smallest].expiryTime) smallest = right;
      if (smallest !== idx) {
        [this.heap[idx], this.heap[smallest]] = [this.heap[smallest], this.heap[idx]];
        idx = smallest;
      } else break;
    }
  }

  rebuild() {
    for (let i = (this.heap.length >> 1) - 1; i >= 0; i--) {
      this.bubbleDown(i);
    }
  }
}

export class TokenBucketSim {
  constructor(capacity = 50, refillRate = 10) {
    this.capacity = capacity;
    this.refillRate = refillRate;
    this.tokens = capacity;
    this.lastRefill = Date.now();
  }

  tryAcquire(cost = 1) {
    const now = Date.now();
    const elapsedSec = (now - this.lastRefill) / 1000;
    this.tokens = Math.min(this.capacity, this.tokens + elapsedSec * this.refillRate);
    this.lastRefill = now;

    if (this.tokens >= cost) {
      this.tokens -= cost;
      return true;
    }
    return false;
  }
}
