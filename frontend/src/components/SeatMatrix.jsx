import React, { useState, useMemo } from 'react';

export default function SeatMatrix({ seatsData, selectedSeats, onSelectRange, onClearSelection, onLockReserve }) {
  const [activeCoach, setActiveCoach] = useState('ALL');
  const [rangeStart, setRangeStart] = useState(null);

  const coaches = useMemo(() => {
    const list = [
      { id: 'ALL', label: 'All (1,000 Seats)' },
      { id: 'S1', label: 'Coach S1 (1-72)' },
      { id: 'S2', label: 'Coach S2 (73-144)' },
      { id: 'S3', label: 'Coach S3 (145-216)' },
      { id: 'S4', label: 'Coach S4 (217-288)' },
      { id: 'S5', label: 'Coach S5 (289-360)' },
      { id: 'S6', label: 'Coach S6 (361-432)' },
      { id: 'S7', label: 'Coach S7 (433-504)' },
      { id: 'S8', label: 'Coach S8 (505-576)' },
      { id: 'S9', label: 'Coach S9 (577-648)' },
      { id: 'S10', label: 'Coach S10 (649-720)' },
      { id: 'S11', label: 'Coach S11 (721-792)' },
      { id: 'S12', label: 'Coach S12 (793-864)' },
      { id: 'S13', label: 'Coach S13 (865-936)' },
      { id: 'S14', label: 'Coach S14 (937-1000)' },
    ];
    return list;
  }, []);

  const groupedSeats = useMemo(() => {
    const groups = {};
    for (const seat of seatsData) {
      if (activeCoach !== 'ALL' && seat.coach !== activeCoach) continue;
      if (!groups[seat.coach]) groups[seat.coach] = [];
      groups[seat.coach].push(seat);
    }
    return groups;
  }, [seatsData, activeCoach]);

  const handleSeatClick = (seatNumber) => {
    const seat = seatsData[seatNumber];
    if (!seat || seat.status === 'BOOKED' || seat.status === 'LOCKED') return;

    if (rangeStart === null) {
      setRangeStart(seatNumber);
      onSelectRange([seatNumber]);
    } else {
      const start = Math.min(rangeStart, seatNumber);
      const end = Math.max(rangeStart, seatNumber);

      // Verify all contiguous seats in range are available
      let allAvailable = true;
      for (let i = start; i <= end; i++) {
        if (seatsData[i].status !== 'AVAILABLE') {
          allAvailable = false;
          break;
        }
      }

      if (allAvailable && (end - start + 1) <= 6) {
        const range = [];
        for (let i = start; i <= end; i++) range.push(i);
        onSelectRange(range);
        setRangeStart(null);
      } else {
        setRangeStart(seatNumber);
        onSelectRange([seatNumber]);
      }
    }
  };

  const selectedSet = useMemo(() => new Set(selectedSeats), [selectedSeats]);

  const startSeatDisplay = selectedSeats.length > 0 ? selectedSeats[0] + 1 : 0;
  const endSeatDisplay = selectedSeats.length > 0 ? selectedSeats[selectedSeats.length - 1] + 1 : 0;
  const totalPrice = selectedSeats.length * 1450;

  return (
    <section className="glass-panel seat-matrix-panel">
      <div className="matrix-header">
        <div>
          <h2 className="matrix-title">
            <span>Train Seat Map</span>
            <span className="brand-badge" style={{ fontSize: '0.65rem' }}>O(log N) SEGMENT TREE</span>
          </h2>
          <p className="matrix-subtitle">Click and select contiguous seats (e.g. 45 to 48) to lock & book.</p>
        </div>

        <div className="seat-legend">
          <div className="legend-item"><span className="legend-dot available"></span> Available</div>
          <div className="legend-item"><span className="legend-dot selected"></span> Selected</div>
          <div className="legend-item"><span className="legend-dot locked"></span> 5-Min Hold</div>
          <div className="legend-item"><span className="legend-dot booked"></span> Confirmed</div>
        </div>
      </div>

      {/* Coach Tabs */}
      <div className="coach-tabs-scroll">
        {coaches.map(c => (
          <button
            key={c.id}
            className={`coach-tab ${activeCoach === c.id ? 'active' : ''}`}
            onClick={() => setActiveCoach(c.id)}
          >
            {c.label}
          </button>
        ))}
      </div>

      {/* 1,000-Seat Grid */}
      <div className="seat-grid-container">
        {Object.entries(groupedSeats).map(([coachName, seats]) => {
          const availableInCoach = seats.filter(s => s.status === 'AVAILABLE').length;
          return (
            <div key={coachName} className="coach-section">
              <div className="coach-header">
                <span className="coach-name">
                  Coach {coachName} ({seats[0].displayNumber} - {seats[seats.length - 1].displayNumber})
                </span>
                <span className="coach-stats">
                  {availableInCoach} / {seats.length} Available
                </span>
              </div>

              <div className="coach-seats-grid">
                {seats.map(seat => {
                  const isSelected = selectedSet.has(seat.seatNumber);
                  let stateClass = seat.status.toLowerCase();
                  if (isSelected) stateClass = 'selected';

                  return (
                    <div
                      key={seat.seatNumber}
                      className={`seat-cell ${stateClass}`}
                      onClick={() => handleSeatClick(seat.seatNumber)}
                    >
                      {seat.seatInCoach}

                      <div className="seat-tooltip">
                        <strong>Seat #{seat.displayNumber} ({seat.coach})</strong><br />
                        Berth: {seat.berthType}<br />
                        Status: <span style={{ fontWeight: 700 }}>{isSelected ? 'SELECTED' : seat.status}</span><br />
                        Fare: ₹{seat.price}
                        {seat.status === 'LOCKED' && (
                          <div style={{ color: '#f59e0b', marginTop: '2px' }}>⏱️ Active 5-min Hold</div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>

      {/* Floating Selection Bar */}
      {selectedSeats.length > 0 && (
        <div className="selection-action-bar">
          <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
            <div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Selected Seat Range</div>
              <div className="selection-seats">
                {selectedSeats.length === 1 
                  ? `Seat #${startSeatDisplay}` 
                  : `Seats #${startSeatDisplay} - #${endSeatDisplay} (${selectedSeats.length} contiguous berths)`}
              </div>
            </div>
            <div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Total Fare</div>
              <div className="selection-price">₹{totalPrice.toLocaleString('en-IN')}</div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '0.75rem' }}>
            <button className="btn btn-secondary" onClick={onClearSelection}>Clear</button>
            <button className="btn btn-primary" onClick={onLockReserve}>⚡ Lock & Reserve Now</button>
          </div>
        </div>
      )}
    </section>
  );
}
