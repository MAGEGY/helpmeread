interface Props {
  volume: number;
  speechRate: number;
  onVolumeChange: (v: number) => void;
  onSpeechRateChange: (r: number) => void;
  onClose: () => void;
  onTest: () => void;
}

export function VoiceSettings({
  volume,
  speechRate,
  onVolumeChange,
  onSpeechRateChange,
  onClose,
  onTest,
}: Props) {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>🔊 Voice Settings</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>

        <div className="settings-section">
          <div className="settings-label-row">
            <span>🔇</span>
            <span className="settings-label">Volume</span>
            <span>🔊</span>
          </div>
          <input
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={volume}
            onChange={(e) => onVolumeChange(parseFloat(e.target.value))}
            className="slider slider-green"
          />
          <div className="settings-value-row">
            <span className="settings-min">Silent</span>
            <span className="settings-val">{Math.round(volume * 100)}%</span>
            <span className="settings-max">Loud</span>
          </div>
        </div>

        <div className="settings-section">
          <div className="settings-label-row">
            <span>🐢</span>
            <span className="settings-label">Speed</span>
            <span>🐇</span>
          </div>
          <input
            type="range"
            min={0.5}
            max={2}
            step={0.1}
            value={speechRate}
            onChange={(e) => onSpeechRateChange(parseFloat(e.target.value))}
            className="slider slider-blue"
          />
          <div className="settings-value-row">
            <span className="settings-min">Slow</span>
            <span className="settings-val">
              {speechRate < 0.8 ? 'Slow' : speechRate > 1.3 ? 'Fast' : 'Normal'}
            </span>
            <span className="settings-max">Fast</span>
          </div>
        </div>

        <button className="btn-test" onClick={onTest}>
          ▶ Test Voice
        </button>
      </div>
    </div>
  );
}
