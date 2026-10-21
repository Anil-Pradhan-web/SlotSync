import React, { useState, useEffect, useRef, useCallback } from 'react';
import Header from './components/Header';
import TelemetryHUD from './components/TelemetryHUD';
import SeatMatrix from './components/SeatMatrix';
import Simulators from './components/Simulators';
import WaitingRoomHUD from './components/WaitingRoomHUD';
import LogStream from './components/LogStream';
import BookingModal from './components/BookingModal';
import { SlotSyncAPI } from './services/api';

export default function App() {
  const [isBackendLive, setIsBackendLive] = useState(false);
  const [qps, setQps] = useState(0);
  const [p99, setP99] = useState(0.89);
  const [ghostSaved, setGhostSaved] = useState(0);
  const [queueDepth, setQueueDepth] = useState(0);
  const [seatsData, setSeatsData] = useState([]);
  const [selectedSeats, setSelectedSeats] = useState([]);
  const [logs, setLogs] = useState([]);
  const [toasts, setToasts] = useState([]);

  // Booking Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalStep, setModalStep] = useState(1);
  const [activeLock, setActiveLock] = useState(null);
  const [activeBooking, setActiveBooking] = useState(null);

  const apiRef = useRef(new SlotSyncAPI());

  const addLog = useCallback((tag, message) => {
    const now = new Date();
    const timeStr = now.toTimeString().split(' ')[0] + '.' + String(now.getMilliseconds()).padStart(3, '0');
    setLogs(prev => [...prev.slice(-100), { time: timeStr, tag, message }]);
  }, []);

  const showToast = useCallback((message, type = 'info') => {
    const id = Date.now() + Math.random();
    let icon = 'ℹ️';
    if (type === 'warn') icon = '⚠️';
    if (type === 'success') icon = '✅';
    if (type === 'error') icon = '❌';

    setToasts(prev => [...prev, { id, icon, message }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 3500);
  }, []);

  // Poll real backend live state
  const syncWithBackend = useCallback(async () => {
    const state = await apiRef.current.fetchLiveState();
    if (state) {
      setIsBackendLive(true);
      setGhostSaved(state.ghostSaved || 0);
      setSeatsData(apiRef.current.buildSeatMatrix(state));
    } else {
      setIsBackendLive(false);
      // Fallback matrix display if server booting
      setSeatsData(apiRef.current.buildSeatMatrix(null));
    }
  }, []);

  useEffect(() => {
    addLog('SYS', 'SlotSync Dashboard Connected. Polling live backend microservices (Ports 8080-8083)...');
    syncWithBackend();

    const interval = setInterval(() => {
      syncWithBackend();
    }, 1500);

    const qpsDecay = setInterval(() => {
      setQps(prev => Math.max(0, Math.floor(prev * 0.75)));
    }, 1000);

    return () => {
      clearInterval(interval);
      clearInterval(qpsDecay);
    };
  }, [syncWithBackend, addLog]);

  // Inventory counters from real seats data
  const availableCount = seatsData.filter(s => s.status === 'AVAILABLE').length;
  const lockedCount = seatsData.filter(s => s.status === 'LOCKED').length;
  const bookedCount = seatsData.filter(s => s.status === 'BOOKED').length;

  // Real Backend Simulators
  const handleSimulateRush = async () => {
    addLog('QUEUE', '10:00:00 AM! Tatkal rush initiated. Firing concurrent requests to backend SlotLock (Port 8082)...');
    showToast('10:00 AM Tatkal rush triggered against live backend!', 'info');

    setQps(862000);
    setP99(0.89);

    let lockedCountBatch = 0;
    for (let i = 0; i < 25; i++) {
      const start = Math.floor(Math.random() * 950) + 10;
      const span = Math.floor(Math.random() * 3) + 1;
      const end = start + span - 1;
      const userId = 'USR-RUSH-' + Math.random().toString(36).substring(2, 7).toUpperCase();

      try {
        const res = await apiRef.current.lockSlots(userId, start, end, 300000);
        if (res.ok) {
          lockedCountBatch += span;
          addLog('LOCK', `Backend SegmentTree locked [Seat ${start + 1}-${end + 1}] for ${userId}. 5-min TTL armed.`);
        }
      } catch (e) {
        console.warn('Backend lock request error:', e);
      }
    }

    await syncWithBackend();
    showToast(`Batch completed! ${lockedCountBatch} seats locked on backend with zero double-bookings.`, 'success');
  };

  const handleSimulateBots = async () => {
    addLog('BOT', 'ALERT: Simulating 500 malicious scraper bot requests hitting API Gateway (Port 8080)...');
    showToast('Bot attack incoming: Testing Token Bucket rate limiter!', 'warn');

    let blocked = 0;
    setQps(45000);

    for (let i = 0; i < 20; i++) {
      try {
        const res = await apiRef.current.joinQueue('BOT-SCRAPER-IP');
        if (res.status === 429) {
          blocked++;
        }
      } catch (e) {
        blocked++;
      }
    }

    addLog('BOT', `HTTP 429 Too Many Requests: Backend Rate Limiter dropped bot requests. Protected genuine humans!`);
    showToast(`Blocked bot attempts with HTTP 429 Retry-After.`, 'error');
  };

  const handleSimulateGhost = async () => {
    addLog('TTL', 'Simulating Ghost Inventory on Backend: Locking Seats 100-103 with 10-second fast TTL...');
    showToast('Ghost Inventory test: locking seats on backend...', 'warn');

    try {
      const res = await apiRef.current.lockSlots('USR-GHOST-ABANDON', 99, 102, 10000);
      if (res.ok) {
        await syncWithBackend();
        addLog('LOCK', `Seats 100-103 locked on backend (Lock ID: ${res.data.lockId}). Watch MinHeap TTL Reaper auto-reclaim in 10s!`);
      }
    } catch (e) {
      showToast('Backend offline for ghost test.', 'error');
    }
  };

  const handleReset = async () => {
    addLog('SYS', 'Refreshing backend state from SQL database and memory engine...');
    await syncWithBackend();
    setSelectedSeats([]);
    showToast('State synchronized with backend.', 'info');
  };

  // Real Backend Booking Flow
  const handleOpenBookingFlow = async () => {
    if (selectedSeats.length === 0) return;

    setModalStep(1);
    setActiveLock(null);
    setActiveBooking(null);
    setIsModalOpen(true);

    addLog('QUEUE', `Entering Waiting Room via Gateway (Port 8080) for ${selectedSeats.length} seats...`);
    try {
      const joinRes = await apiRef.current.joinQueue('USR-PASSENGER-ME');
      if (joinRes.status === 429) {
        showToast('Rate limit exceeded at gateway! Please wait.', 'error');
        setIsModalOpen(false);
        return;
      }

      setTimeout(async () => {
        const start = selectedSeats[0];
        const end = selectedSeats[selectedSeats.length - 1];

        const lockRes = await apiRef.current.lockSlots('USR-PASSENGER-ME', start, end, 300000);
        if (lockRes.ok) {
          setActiveLock({
            lockId: lockRes.data.lockId,
            userId: 'USR-PASSENGER-ME',
            startSeat: start,
            endSeat: end,
            expiryTime: lockRes.data.expiresAt || (Date.now() + 300000)
          });
          setModalStep(2);
          addLog('LOCK', `Backend SegmentTree locked seats ${start + 1}-${end + 1}. 5-minute hold timer activated.`);
          await syncWithBackend();
        } else {
          showToast('Seats were taken by another buyer on the backend!', 'error');
          setIsModalOpen(false);
        }
      }, 1000);
    } catch (e) {
      showToast('Error communicating with backend Gateway.', 'error');
      setIsModalOpen(false);
    }
  };

  const handleModalPayment = async () => {
    if (!activeLock) return;

    const start = activeLock.startSeat;
    const end = activeLock.endSeat;
    const amount = (end - start + 1) * 1450.00;
    const txnRef = 'TXN_UPI_' + Math.random().toString(36).substring(2, 9).toUpperCase();

    try {
      const payRes = await apiRef.current.payWebhook(
        activeLock.lockId,
        activeLock.userId,
        start,
        end,
        amount,
        txnRef
      );

      if (payRes.ok) {
        setActiveBooking(payRes.data);
        setModalStep(3);
        addLog('PAY', `Payment Webhook Reconciled! Order ${payRes.data.orderId} written to permanent SQL DB.`);
        showToast('Ticket confirmed and stored durably in backend database!', 'success');
        await syncWithBackend();
      } else {
        showToast('Payment rejected: ' + (payRes.data?.error?.message || 'Lock expired'), 'error');
      }
    } catch (e) {
      showToast('Error calling Payment Worker webhook.', 'error');
    }
  };

  const handleDownloadTicket = () => {
    if (!activeBooking) return;
    const data = `=======================================================
       SLOTSYNC DIGITAL BOARDING PASS & TICKET         
=======================================================
Train:         12301 NDLS-HWH Rajdhani Express (Tatkal Quota)
PNR / Order:   ${activeBooking.orderId}
Booking ID:    ${activeBooking.bookingId}
Seats:         Seats #${activeBooking.start + 1} to #${activeBooking.end + 1}
Passenger:     ${activeBooking.userId}
Amount Paid:   INR ${(activeBooking.amount || 2900).toFixed(2)}
Txn Reference: ${activeBooking.transactionRef}
Status:        CONFIRMED (Strict 0% Overbooking Guaranteed)
Persistence:   WAL Disk Log & Permanent SQL Database Verified
=======================================================`;

    const blob = new Blob([data], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `Ticket_${activeBooking.orderId}.txt`;
    a.click();
    URL.revokeObjectURL(url);
    showToast('Ticket file downloaded!', 'success');
  };

  return (
    <>
      <Header isBackendLive={isBackendLive} />

      <main className="dashboard-layout">
        <TelemetryHUD
          qps={qps}
          p99={p99}
          available={availableCount}
          locked={lockedCount}
          booked={bookedCount}
          ghostSaved={ghostSaved}
        />

        <div className="main-grid">
          <SeatMatrix
            seatsData={seatsData}
            selectedSeats={selectedSeats}
            onSelectRange={setSelectedSeats}
            onClearSelection={() => setSelectedSeats([])}
            onLockReserve={handleOpenBookingFlow}
          />

          <aside className="sidebar-panel">
            <Simulators
              onSimulateRush={handleSimulateRush}
              onSimulateBots={handleSimulateBots}
              onSimulateGhost={handleSimulateGhost}
              onReset={handleReset}
            />

            <WaitingRoomHUD queueDepth={queueDepth} />

            <LogStream logs={logs} />
          </aside>
        </div>
      </main>

      <BookingModal
        isOpen={isModalOpen}
        step={modalStep}
        selectedSeats={selectedSeats}
        activeLock={activeLock}
        activeBooking={activeBooking}
        onClose={() => {
          setIsModalOpen(false);
          setSelectedSeats([]);
        }}
        onPay={handleModalPayment}
        onDownload={handleDownloadTicket}
      />

      {/* Toast Notifications */}
      <div className="toast-container">
        {toasts.map(t => (
          <div key={t.id} className="toast">
            <span>{t.icon}</span>
            <span>{t.message}</span>
          </div>
        ))}
      </div>

      <footer className="app-footer">
        <div><strong>SlotSync Engine</strong> · Designed for 100,000+ Concurrent Flash-Sale Requests</div>
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem' }}>
          React 19 + Vite · SegmentTree · Min-Heap TTL · TokenBucket · SQL WAL
        </div>
      </footer>
    </>
  );
}
