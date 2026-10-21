import React from 'react';

export default function Simulators({ onSimulateRush, onSimulateBots, onSimulateGhost, onReset }) {
  return (
    <div className="glass-panel panel-section">
      <div className="panel-title">
        <div className="panel-icon-title">
          <span>🎮</span>
          <span>Flash-Sale Simulators</span>
        </div>
        <span className="brand-badge">INTERACTIVE</span>
      </div>

      <div className="sim-button-grid">
        <button className="btn btn-rush" onClick={onSimulateRush}>
          <span>⚡</span>
          <span>10:00 AM Tatkal Rush (10k Users)</span>
        </button>

        <button className="btn btn-danger" onClick={onSimulateBots}>
          <span>🛡️</span>
          <span>Launch 500-Bot Scraper Attack</span>
        </button>

        <button className="btn btn-secondary" onClick={onSimulateGhost}>
          <span>⏱️</span>
          <span>Simulate Ghost Inventory TTL</span>
        </button>

        <button className="btn btn-secondary" style={{ borderStyle: 'dashed' }} onClick={onReset}>
          <span>🔄</span>
          <span>Reset & Re-Seed 1,000 Seats</span>
        </button>
      </div>
    </div>
  );
}
