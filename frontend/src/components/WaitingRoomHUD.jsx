import React from 'react';

export default function WaitingRoomHUD({ queueDepth }) {
  return (
    <div className="glass-panel panel-section">
      <div className="panel-title">
        <div className="panel-icon-title">
          <span>🚪</span>
          <span>Virtual Waiting Room</span>
        </div>
        <span className="metric-badge" style={{ background: 'rgba(139,92,246,0.15)', color: '#a78bfa' }}>
          200 USERS/SEC
        </span>
      </div>

      <div className="queue-hud-box">
        <div className="queue-header">
          <span>Waiting Line Depth</span>
          <span className="queue-number">{queueDepth}</span>
        </div>
        <div className="metric-progress">
          <div className="metric-progress-fill purple" style={{ width: queueDepth > 0 ? '70%' : '15%' }}></div>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          <span>Throttle Filter: Token Bucket</span>
          <span>Batch Cadence: 1,000 ms</span>
        </div>
      </div>
    </div>
  );
}
