import React, { useState, useEffect } from 'react';

export default function Header({ isBackendLive }) {
  const [clock, setClock] = useState('');

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setClock(now.toLocaleTimeString('en-IN', { hour12: false }) + ' IST');
    };
    updateTime();
    const timer = setInterval(updateTime, 1000);
    return () => clearInterval(timer);
  }, []);

  return (
    <header className="app-header">
      <div className="header-container">
        <div className="brand-section">
          <img src="/favicon.svg" alt="SlotSync Logo" style={{ width: '38px', height: '38px', borderRadius: '10px' }} />
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <h1 className="brand-title">SlotSync</h1>
              <span className="brand-badge">⚡ HIGH-SPEED BOOKING</span>
            </div>
          </div>
        </div>

        <div className="train-info-pill">
          <span className="train-status-dot"></span>
          <span className="train-title">Rajdhani Express 12301</span>
          <span className="train-route">NDLS ➔ HWH · 1,000 Tatkal Seats</span>
        </div>

        <div className="header-actions">
          <div className="tatkal-clock">
            <span className="clock-icon">🕒</span>
            <span>{clock}</span>
          </div>
          <div className={`mode-badge ${isBackendLive ? '' : 'sim-mode'}`}>
            <span 
              className="train-status-dot" 
              style={{ background: isBackendLive ? '#10b981' : '#8b5cf6' }}
            ></span>
            <span>{isBackendLive ? '🟢 Backend Live' : '🟣 Demo Mode'}</span>
          </div>
        </div>
      </div>
    </header>
  );
}
