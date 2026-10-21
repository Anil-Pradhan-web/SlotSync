import React from 'react';

export default function WaitingRoomHUD({ 
  queueDepth = 0, 
  isRushSimulating = false, 
  isTrainFull = false,
  totalDemand = 10000 
}) {
  const isAdmitting = isRushSimulating && queueDepth > 0;
  const isClosed = !isRushSimulating && (isTrainFull || queueDepth > 0);
  
  // Dynamic percentage relative to peak capacity
  const fillPercentage = isAdmitting 
    ? Math.min(100, Math.max(12, Math.round((queueDepth / totalDemand) * 100))) 
    : isClosed ? 100 : 0;
    
  const estWaitSeconds = (queueDepth / 200).toFixed(1);

  return (
    <div className={`glass-panel panel-section queue-panel-container ${isAdmitting ? 'queue-active-glow' : ''}`}>
      <div className="panel-title">
        <div className="panel-icon-title">
          <span className={`turnstile-icon ${isAdmitting ? 'spinning-gate' : ''}`}>
            {isClosed ? '🔒' : '🚪'}
          </span>
          <span>Smart Waiting Line</span>
        </div>

        {isAdmitting ? (
          <span className="metric-badge purple-pulse">
            <span className="live-dot purple"></span>
            ADMITTING: 200/s
          </span>
        ) : isClosed ? (
          <span 
            className="metric-badge" 
            style={{ 
              background: 'rgba(239, 68, 68, 0.15)', 
              color: '#f87171', 
              border: '1px solid rgba(239, 68, 68, 0.35)' 
            }}
          >
            <span className="live-dot" style={{ background: '#ef4444', boxShadow: '0 0 8px #ef4444' }}></span>
            🛑 GATE CLOSED
          </span>
        ) : (
          <span className="metric-badge green-badge">
            <span className="live-dot green"></span>
            DIRECT ENTRY
          </span>
        )}
      </div>

      <div className="queue-hud-box">
        <div className="queue-header">
          <span>
            {isAdmitting 
              ? 'Passengers in Waiting Line' 
              : isClosed 
                ? 'Tatkal Line Status (Train Housefull)' 
                : 'Line Status'}
          </span>
          <span className={`queue-number ${isAdmitting ? 'queue-active-count' : isClosed ? 'queue-closed-count' : 'queue-idle-count'}`}
                style={isClosed ? { color: '#f87171' } : {}}>
            {isAdmitting 
              ? queueDepth.toLocaleString('en-IN') 
              : isClosed 
                ? `${(queueDepth || 9006).toLocaleString('en-IN')} (WAITLISTED)` 
                : '0 (CLEAR)'}
          </span>
        </div>

        {/* Dynamic Animated Progress Fill */}
        <div className="queue-progress-track">
          <div 
            className={`queue-progress-bar ${isAdmitting ? 'shimmer-active' : ''}`}
            style={{ 
              width: `${isAdmitting ? fillPercentage : isClosed ? 100 : 4}%`,
              background: isClosed ? 'linear-gradient(90deg, #ef4444, #f59e0b)' : undefined 
            }}
          ></div>
        </div>

        {/* Dynamic Turnstile Flow Visualizer */}
        <div className="turnstile-flow-container">
          <div className="flow-step">
            <span className="flow-icon">{isClosed ? '🛑' : '👥'}</span>
            <span className="flow-label">{isClosed ? 'Line Closed' : 'Waiting'}</span>
          </div>

          <div className="flow-connector">
            <div className={`flow-beam ${isAdmitting ? 'beam-traveling' : 'beam-idle'}`}>
              {isAdmitting && (
                <>
                  <span className="flow-particle p1"></span>
                  <span className="flow-particle p2"></span>
                  <span className="flow-particle p3"></span>
                </>
              )}
            </div>
            <span className="flow-gate-badge" style={isClosed ? { borderColor: 'rgba(239,68,68,0.5)', color: '#f87171' } : {}}>
              {isAdmitting ? '200 / SEC' : isClosed ? '🔒 LOCKED' : 'DIRECT PASS'}
            </span>
          </div>

          <div className="flow-step">
            <span className="flow-icon">⚡</span>
            <span className="flow-label">RAM Engine</span>
          </div>
        </div>

        {/* Dynamic Telemetry Subtext */}
        <div className="queue-footer-row">
          <span>
            {isAdmitting ? (
              <>⏱️ Est. Wait: <strong style={{ color: '#c084fc' }}>~{estWaitSeconds}s</strong></>
            ) : isClosed ? (
              <span style={{ color: '#fca5a5' }}>⛔ Train 100% Full · Fair Drop</span>
            ) : (
              <>⚡ Fast-Track Lane: <strong style={{ color: 'var(--status-available)' }}>0ms Delay</strong></>
            )}
          </span>
          <span>
            🛡️ <span style={{ color: 'var(--text-muted)' }}>{isClosed ? 'Zero Overbooking' : 'Token Bucket Protected'}</span>
          </span>
        </div>
      </div>
    </div>
  );
}

