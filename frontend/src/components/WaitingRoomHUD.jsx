import React from 'react';

export default function WaitingRoomHUD({ queueDepth }) {
  return (
    <div className="glass-panel panel-section">
      <div className="panel-title">
        <div className="panel-icon-title">
          <span>🚪</span>
          <span>Smart Waiting Line</span>
        </div>
        <span className="metric-badge" style={{ background: 'rgba(139,92,246,0.15)', color: '#a78bfa' }}>
          FAIR QUEUE
        </span>
      </div>

      <div className="queue-hud-box">
        <div className="queue-header">
          <span>People Waiting in Line</span>
          <span className="queue-number">{queueDepth}</span>
        </div>
        <div className="metric-progress">
          <div className="metric-progress-fill purple" style={{ width: queueDepth > 0 ? '70%' : '15%' }}></div>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          <span>Entry Speed: 200 users/sec</span>
          <span>Zero Server Hang</span>
        </div>
      </div>
    </div>
  );
}
