import React from 'react';

export default function Simulators({ onSimulateRush, onSimulateBots, onReset, isRushSimulating }) {
  return (
    <div className="glass-panel panel-section">
      <div className="panel-title">
        <div className="panel-icon-title">
          <span>🎮</span>
          <span>Live Test Buttons</span>
        </div>
        <span className="brand-badge">INTERACTIVE</span>
      </div>

      <div className="sim-button-grid">
        <button 
          className={`btn ${isRushSimulating ? 'btn-danger' : 'btn-rush'}`} 
          onClick={onSimulateRush}
          style={isRushSimulating ? { animation: 'pulse 1.2s infinite' } : {}}
        >
          <span>{isRushSimulating ? '⏸️' : '⚡'}</span>
          <span>{isRushSimulating ? 'Stop Tatkal Rush Simulation' : '10:00 AM Tatkal Rush (10k Users)'}</span>
        </button>

        <button className="btn btn-danger" onClick={onSimulateBots}>
          <span>🛡️</span>
          <span>Block 500 Fake Bots (Security Test)</span>
        </button>

        <button className="btn btn-secondary" style={{ borderStyle: 'dashed' }} onClick={onReset}>
          <span>🔄</span>
          <span>Reset Train (Clear All & Start Fresh)</span>
        </button>
      </div>
    </div>
  );
}
