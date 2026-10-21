import React, { useEffect, useRef } from 'react';

export default function LogStream({ logs }) {
  const streamRef = useRef(null);

  useEffect(() => {
    if (streamRef.current) {
      streamRef.current.scrollTop = streamRef.current.scrollHeight;
    }
  }, [logs]);

  return (
    <div className="glass-panel panel-section">
      <div className="panel-title">
        <div className="panel-icon-title">
          <span>📡</span>
          <span>Live Activity Log</span>
        </div>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.7rem', color: 'var(--status-available)' }}>
          REAL-TIME
        </span>
      </div>

      <div className="log-drawer">
        <div className="log-drawer-header">
          <span>SERVER EVENTS</span>
          <span>TIMESTAMPED LOG</span>
        </div>

        <div ref={streamRef} className="log-stream">
          {logs.map((entry, index) => (
            <div key={index} className="log-entry">
              <span className="log-time">[{entry.time}]</span>
              <span className={`log-tag ${entry.tag.toLowerCase()}`}>{entry.tag}</span>
              <span className="log-msg">{entry.message}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
