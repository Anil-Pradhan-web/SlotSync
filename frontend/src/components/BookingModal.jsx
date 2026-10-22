import React, { useState, useEffect } from 'react';

export default function BookingModal({
  isOpen,
  step,
  selectedSeats,
  activeLock,
  activeBooking,
  trainInfo,
  onClose,
  onPay,
  onDownload
}) {
  const [countdown, setCountdown] = useState('05:00');
  const [isProcessingPay, setIsProcessingPay] = useState(false);

  useEffect(() => {
    if (!isOpen || step !== 1 || !activeLock) return;

    const timer = setInterval(() => {
      const remainingMs = Math.max(0, activeLock.expiryTime - Date.now());
      const totalSec = Math.floor(remainingMs / 1000);
      const min = Math.floor(totalSec / 60);
      const sec = totalSec % 60;
      setCountdown(`${String(min).padStart(2, '0')}:${String(sec).padStart(2, '0')}`);

      if (remainingMs <= 0) {
        clearInterval(timer);
        onClose();
      }
    }, 1000);

    return () => clearInterval(timer);
  }, [isOpen, step, activeLock, onClose]);

  if (!isOpen) return null;

  const startSeat = selectedSeats.length > 0 ? selectedSeats[0] + 1 : 0;
  const endSeat = selectedSeats.length > 0 ? selectedSeats[selectedSeats.length - 1] + 1 : 0;
  const basePrice = trainInfo?.basePrice || 1450;
  const fare = selectedSeats.length * basePrice;

  const handlePayClick = async () => {
    setIsProcessingPay(true);
    await onPay();
    setIsProcessingPay(false);
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content">
        <div className="modal-header">
          <h3 className="modal-title">🎟️ Instant Seat Hold &amp; Payment</h3>
          <button className="modal-close" onClick={onClose}>&times;</button>
        </div>

        {/* Stepper Header (Clean 2-Step) */}
        <div className="booking-stepper">
          <div className={`step-item ${step === 1 ? 'active' : step > 1 ? 'done' : ''}`}>
            <div className="step-circle">1</div>
            <span className="step-label">5-Min Hold &amp; Pay</span>
          </div>
          <div className={`step-item ${step === 2 ? 'active' : ''}`}>
            <div className="step-circle">2</div>
            <span className="step-label">Confirmed Ticket</span>
          </div>
        </div>

        {/* Step 1: 5-Minute Hold & Payment */}
        {step === 1 && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
              <div>
                <div style={{ fontSize: '0.775rem', color: 'var(--text-secondary)' }}>Aapki Selected Seats</div>
                <strong style={{ color: 'var(--color-cyan-glow)' }}>
                  {selectedSeats.length === 1 ? `Seat #${startSeat}` : `Seats #${startSeat} - #${endSeat}`} ({trainInfo?.trainName || 'Prashanti Express'})
                </strong>
              </div>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '0.775rem', color: 'var(--text-secondary)' }}>Total Fare</div>
                <strong style={{ fontSize: '1.15rem', color: '#fff' }}>₹{fare.toLocaleString('en-IN')}</strong>
              </div>
            </div>

            <div className="modal-timer-box">
              <div>
                <div style={{ fontSize: '0.75rem', color: 'var(--status-locked)', fontWeight: 700 }}>
                  ⏱️ 5-MINUTE SEAT HOLD TIMER
                </div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  Kripya timer khatam hone se pehle pay karein, warna seats automatically dusre passengers ke liye free ho jayengi.
                </div>
              </div>
              <div className="timer-countdown">{countdown}</div>
            </div>

            <div style={{ marginTop: '1.25rem', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <button
                className="btn btn-primary"
                style={{ width: '100%', padding: '0.85rem' }}
                onClick={handlePayClick}
                disabled={isProcessingPay}
              >
                <span>💳</span>
                <span>{isProcessingPay ? 'Confirming Payment...' : `Pay ₹${fare.toLocaleString('en-IN')} via Instant UPI`}</span>
              </button>
              <button className="btn btn-secondary" style={{ width: '100%' }} onClick={onClose}>
                <span>Cancel &amp; Free Seats</span>
              </button>
            </div>
          </div>
        )}

        {/* Step 2: Confirmed Ticket */}
        {step === 2 && activeBooking && (
          <div style={{ textAlign: 'center', padding: '1rem 0' }}>
            <div
              style={{
                width: '48px',
                height: '48px',
                borderRadius: '50%',
                background: 'rgba(16,185,129,0.15)',
                border: '2px solid var(--status-available)',
                color: 'var(--status-available)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '1.5rem',
                margin: '0 auto 0.75rem'
              }}
            >
              ✓
            </div>
            <h4 style={{ fontSize: '1.15rem', color: '#fff', marginBottom: '0.25rem' }}>Booking Confirmed!</h4>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginBottom: '1.25rem' }}>
              Ticket confirm ho gayi aur database me securely save ho chuki hai. Zero double-booking guaranteed.
            </p>

            <div
              style={{
                background: '#05070c',
                border: '1px solid var(--border-highlight)',
                borderRadius: '8px',
                padding: '1rem',
                textAlign: 'left',
                fontFamily: 'var(--font-mono)',
                fontSize: '0.8rem',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.4rem',
                marginBottom: '1.25rem'
              }}
            >
              <div><span style={{ color: 'var(--text-muted)' }}>PNR / Order:</span> <strong style={{ color: 'var(--color-cyan-glow)' }}>{activeBooking.orderId}</strong></div>
              <div><span style={{ color: 'var(--text-muted)' }}>Berths:</span> <strong>Seats #{(activeBooking.start ?? activeBooking.startSeat ?? 0) + 1} to #{(activeBooking.end ?? activeBooking.endSeat ?? 0) + 1}</strong></div>
              <div><span style={{ color: 'var(--text-muted)' }}>Train:</span> {trainInfo?.trainNumber || '18463'} {trainInfo?.trainName || 'Prashanti Express'} (Coach {'S' + (Math.floor(((activeBooking.start ?? activeBooking.startSeat ?? 0)) / 72) + 1)} · {trainInfo?.route || 'BBS ➔ SBC'})</div>
              <div><span style={{ color: 'var(--text-muted)' }}>Txn Ref:</span> <span style={{ color: 'var(--status-available)' }}>{activeBooking.transactionRef}</span></div>
            </div>

            <button className="btn btn-primary" style={{ width: '100%' }} onClick={onDownload}>
              <span>📥</span>
              <span>Download Ticket</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
