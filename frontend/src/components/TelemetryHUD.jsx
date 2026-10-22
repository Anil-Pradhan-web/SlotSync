import React from 'react';

export default function TelemetryHUD({ qps, p99, available, locked, booked, ghostSaved }) {
  return (
    <section className="telemetry-grid">
      {/* 1. Throughput QPS */}
      <div className="glass-panel metric-card">
        <div className="metric-header">
          <span className="metric-label">Booking Speed</span>
          <span className={`metric-badge ${qps > 0 ? 'cyan' : ''}`} style={qps === 0 ? { background: 'rgba(148, 163, 184, 0.12)', color: 'var(--text-muted)', border: '1px solid rgba(148, 163, 184, 0.25)' } : {}}>
            {qps > 0 ? '⚡ HIGH THROUGHPUT' : 'IDLE / STANDBY'}
          </span>
        </div>
        <div className="metric-value-row">
          <span className="metric-value" style={qps === 0 ? { color: 'var(--text-secondary)' } : {}}>
            {qps.toLocaleString('en-IN')}
          </span>
          <span className="metric-unit">Req / Sec</span>
        </div>
        <div className="metric-progress">
          <div
            className="metric-progress-fill cyan"
            style={{ width: qps > 0 ? `${Math.min(100, Math.max(12, Math.floor((qps / 862000) * 100)))}%` : '0%' }}
          ></div>
        </div>
        <div className="metric-subtext">Peak tested: 862,000 requests/sec</div>
      </div>

      {/* 2. Response Time */}
      <div className="glass-panel metric-card">
        <div className="metric-header">
          <span className="metric-label">Response Time</span>
          <span className="metric-badge green">RAM SPEED</span>
        </div>
        <div className="metric-value-row">
          <span className="metric-value">{p99}</span>
          <span className="metric-unit">ms</span>
        </div>
        <div className="metric-progress">
          <div className="metric-progress-fill green" style={{ width: '25%' }}></div>
        </div>
        <div className="metric-subtext">Instant answer: Sub-millisecond (&lt; 1 ms)</div>
      </div>

      {/* 3. Double-Booking Rate */}
      <div className="glass-panel metric-card">
        <div className="metric-header">
          <span className="metric-label">Duplicate Seats</span>
          <span className="metric-badge green">STRICT ZERO</span>
        </div>
        <div className="metric-value-row">
          <span className="metric-value" style={{ color: 'var(--status-available)' }}>0.00%</span>
          <span className="metric-unit">ERRORS</span>
        </div>
        <div className="metric-progress">
          <div className="metric-progress-fill green" style={{ width: '100%' }}></div>
        </div>
        <div className="metric-subtext">Zero double-booking: 1 Seat = 1 Person Only</div>
      </div>

      {/* 4. Inventory Distribution */}
      <div className="glass-panel metric-card">
        <div className="metric-header">
          <span className="metric-label">Live Seat Status</span>
          <span className="metric-badge amber">TOTAL: 1,000</span>
        </div>
        <div className="metric-value-row" style={{ fontSize: '1.15rem', gap: '1rem' }}>
          <div>
            <span style={{ color: 'var(--status-available)', fontWeight: 700 }}>{available}</span>{' '}
            <small style={{ color: 'var(--text-muted)' }}>Free</small>
          </div>
          <div>
            <span style={{ color: 'var(--status-locked)', fontWeight: 700 }}>{locked}</span>{' '}
            <small style={{ color: 'var(--text-muted)' }}>Hold</small>
          </div>
          <div>
            <span style={{ color: 'var(--status-booked)', fontWeight: 700 }}>{booked}</span>{' '}
            <small style={{ color: 'var(--text-muted)' }}>Booked</small>
          </div>
        </div>
        <div className="metric-progress">
          <div className="metric-progress-fill amber" style={{ width: `${Math.min(100, ((locked + booked) / 1000) * 100)}%` }}></div>
        </div>
        <div className="metric-subtext">
          Unpaid Seats Auto-Freed: <strong style={{ color: '#f59e0b', marginLeft: '4px' }}>{ghostSaved}</strong>
        </div>
      </div>
    </section>
  );
}
