import React, { useEffect, useRef } from 'react';

export default function SecurityBotModal({ isOpen, onClose, attackData, onRetest }) {
  const terminalEndRef = useRef(null);

  useEffect(() => {
    if (terminalEndRef.current) {
      terminalEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [attackData?.packets]);

  if (!isOpen || !attackData) return null;

  const { inProgress, blockedCount, totalBots = 500, tokensLeft, packets = [] } = attackData;
  const progressPercent = Math.min(100, Math.round((blockedCount / totalBots) * 100));

  return (
    <div className="modal-backdrop">
      <div className="modal-content security-bot-modal">
        {/* Modal Header */}
        <div className="modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.65rem' }}>
            <span style={{ fontSize: '1.4rem' }}>🛡️</span>
            <div>
              <div className="modal-title" style={{ fontSize: '1.15rem', fontWeight: 800 }}>
                API Gateway Token Bucket Shield
              </div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                Edge Rate Limiting &amp; Anti-Scraper Bot Mitigation Filter
              </div>
            </div>
          </div>
          <button className="modal-close" onClick={onClose} disabled={inProgress}>✕</button>
        </div>

        <div className="bot-modal-body">
          {/* Top Status Banner */}
          <div className={`bot-status-banner ${inProgress ? 'banner-alert' : 'banner-success'}`}>
            <span className={`live-dot ${inProgress ? 'red-pulse' : 'green'}`}></span>
            <strong>
              {inProgress 
                ? `🚨 500 BOT FLOOD INCOMING: Token Bucket actively dropping scraper traffic...`
                : `🛡️ ATTACK DEFLECTED: 500/500 Malicious Bots Neutralized with HTTP 429!`
              }
            </strong>
          </div>

          {/* Defense Telemetry Metrics Grid */}
          <div className="bot-metrics-grid">
            <div className="bot-metric-card">
              <div className="metric-title">Inbound Threat Burst</div>
              <div className="metric-value threat-val">
                {blockedCount} <span className="metric-sub">/ {totalBots} Req</span>
              </div>
              <div className="metric-note">500 Bots across 3 Subnets</div>
            </div>

            <div className="bot-metric-card">
              <div className="metric-title">HTTP 429 Drop Rate</div>
              <div className="metric-value drop-val">
                {blockedCount > 0 ? '100.0%' : '0%'}
              </div>
              <div className="metric-note">Rate Limit Exceeded Rejection</div>
            </div>

            <div className="bot-metric-card">
              <div className="metric-title">Token Bucket Reserve</div>
              <div className="metric-value token-val">
                {tokensLeft} <span className="metric-sub">/ 50 Tokens</span>
              </div>
              <div className="metric-note">Refill Rate: 10 tokens/sec</div>
            </div>

            <div className="bot-metric-card">
              <div className="metric-title">Core Engine Load</div>
              <div className="metric-value load-val">0.0%</div>
              <div className="metric-note">Zero DB Queries Reached</div>
            </div>
          </div>

          {/* Threat Progress Bar */}
          <div className="bot-progress-section">
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', marginBottom: '0.4rem' }}>
              <span>Threat Neutralization Progress</span>
              <strong style={{ color: inProgress ? '#f59e0b' : '#10b981' }}>{progressPercent}% Blocked</strong>
            </div>
            <div className="bot-progress-track">
              <div 
                className="bot-progress-fill" 
                style={{ 
                  width: `${progressPercent}%`,
                  background: inProgress 
                    ? 'linear-gradient(90deg, #ef4444, #f59e0b)' 
                    : 'linear-gradient(90deg, #10b981, #06b6d4)'
                }}
              ></div>
            </div>
          </div>

          {/* Real-Time Packet Interception Stream */}
          <div className="bot-terminal-container">
            <div className="terminal-header">
              <span>⚡ LIVE PACKET INTERCEPTOR (GATEWAY PORT 8080)</span>
              <span style={{ fontSize: '0.7rem', color: '#94a3b8' }}>Protocol: HTTP/1.1 REST</span>
            </div>
            <div className="terminal-body">
              {packets.map((pkt, i) => (
                <div key={i} className={`packet-line ${pkt.isHuman ? 'human-packet' : 'bot-packet'}`}>
                  <span className="packet-time">{pkt.time}</span>
                  <span className="packet-ip">{pkt.ip}</span>
                  <span className="packet-agent">{pkt.agent}</span>
                  <span className={`packet-badge ${pkt.isHuman ? 'badge-pass' : 'badge-blocked'}`}>
                    {pkt.isHuman ? '200 OK (ALLOWED)' : '429 BLOCKED'}
                  </span>
                </div>
              ))}
              <div ref={terminalEndRef} />
            </div>
          </div>

          {/* Technical Guarantee Notice */}
          <div className="bot-guarantee-row">
            <span>🛡️</span>
            <div>
              <strong>Atomic CAS Rate-Limiter (O(1) Memory Evaluation)</strong>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                Java TokenBucket evaluates client IP tokens without locking databases or Redis. Human traffic is unaffected.
              </div>
            </div>
          </div>

          {/* Footer Controls */}
          <div className="bot-footer-actions">
            {!inProgress && (
              <button className="btn btn-secondary" onClick={onRetest} style={{ flex: 1, padding: '0.75rem' }}>
                🔄 Retest 500 Bots Attack
              </button>
            )}
            <button 
              className="btn btn-primary" 
              onClick={onClose} 
              disabled={inProgress}
              style={{ flex: inProgress ? 1 : 1, padding: '0.75rem', fontWeight: 700 }}
            >
              {inProgress ? 'Testing Firewall Defense...' : '✅ Close Security Monitor'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
