import { useState } from 'react';
import { HistoryService, type HistoryItem } from '../services/history';

interface Props {
  onBack: () => void;
  onReadAloud: (text: string) => void;
}

export function HistoryScreen({ onBack, onReadAloud }: Props) {
  const [items, setItems] = useState<HistoryItem[]>(HistoryService.getItems());
  const [showClear, setShowClear] = useState(false);

  const handleDelete = (id: number) => {
    HistoryService.deleteItem(id);
    setItems(HistoryService.getItems());
  };

  const handleClear = () => {
    HistoryService.clearAll();
    setItems([]);
    setShowClear(false);
  };

  return (
    <div className="screen history-screen">
      <div className="history-header">
        <button className="btn-icon" onClick={onBack}>←</button>
        <h1 className="history-title">History</h1>
        {items.length > 0 ? (
          <button className="btn-icon btn-icon-danger" onClick={() => setShowClear(true)}>🗑</button>
        ) : (
          <div style={{ width: 48 }} />
        )}
      </div>

      {items.length === 0 ? (
        <div className="history-empty">
          <div className="history-empty-icon">🕐</div>
          <p className="history-empty-text">No saved texts yet</p>
          <p className="history-empty-sub">Read text and tap Save to keep it here</p>
        </div>
      ) : (
        <div className="history-list">
          {items.map((item) => (
            <div
              key={item.id}
              className="history-card"
              onClick={() => onReadAloud(item.text)}
            >
              <div className="history-card-top">
                <span className="history-time">
                  {HistoryService.formatTimestamp(item.timestamp)}
                </span>
                <button
                  className="btn-icon-sm btn-icon-danger"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleDelete(item.id);
                  }}
                >
                  🗑
                </button>
              </div>
              <p className="history-text">{item.text}</p>
              <p className="history-meta">{item.blockCount} text areas • Tap to read aloud</p>
            </div>
          ))}
        </div>
      )}

      {showClear && (
        <div className="modal-overlay" onClick={() => setShowClear(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h2>Clear All History</h2>
            <p>Delete all saved texts? This cannot be undone.</p>
            <div className="modal-actions">
              <button className="btn-cancel" onClick={() => setShowClear(false)}>Cancel</button>
              <button className="btn-danger" onClick={handleClear}>Delete All</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
