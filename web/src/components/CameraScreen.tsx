import { useRef, useState } from 'react';
import type { Language } from '../services/languages';
import { LanguageSelector } from './LanguageSelector';
import { VoiceSettings } from './VoiceSettings';

interface Props {
  selectedLanguage: Language;
  onLanguageChange: (lang: Language) => void;
  volume: number;
  speechRate: number;
  onVolumeChange: (v: number) => void;
  onSpeechRateChange: (r: number) => void;
  onTestVoice: () => void;
  onCapture: (file: File) => void;
  onShowHistory: () => void;
  ttsReady: boolean;
}

export function CameraScreen({
  selectedLanguage,
  onLanguageChange,
  volume,
  speechRate,
  onVolumeChange,
  onSpeechRateChange,
  onTestVoice,
  onCapture,
  onShowHistory,
  ttsReady,
}: Props) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [showLang, setShowLang] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [flashOn, setFlashOn] = useState(false);

  const handleFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) onCapture(file);
    // Reset so the same file can be selected again
    e.target.value = '';
  };

  return (
    <div className="screen camera-screen">
      {/* Top hint */}
      <div className="camera-hint">
        📷 Point camera at text — or upload an image
      </div>

      {/* History button */}
      <button className="btn-icon btn-top-left" onClick={onShowHistory} title="History">
        🕐
      </button>

      {/* Upload area */}
      <div className="upload-area">
        <div className="upload-icon">📄</div>
        <p className="upload-text">
          Tap the green button to capture with your camera,<br />
          or upload an image file
        </p>
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          capture="environment"
          onChange={handleFile}
          style={{ display: 'none' }}
        />
      </div>

      {/* Bottom controls */}
      <div className="camera-controls">
        <div className="control-row">
          <button
            className="btn-circle btn-circle-lang"
            onClick={() => setShowLang(true)}
            title="Language"
          >
            {selectedLanguage.flag}
          </button>

          <button
            className={`btn-circle ${flashOn ? 'btn-circle-flash' : ''}`}
            onClick={() => setFlashOn(!flashOn)}
            title="Flash (visual only on web)"
          >
            {flashOn ? '⚡' : '🔦'}
          </button>

          <button
            className="btn-circle"
            onClick={() => setShowSettings(true)}
            title="Voice Settings"
          >
            🔊
          </button>
        </div>

        <button
          className={`btn-capture ${!ttsReady ? 'btn-disabled' : ''}`}
          onClick={() => fileInputRef.current?.click()}
          disabled={!ttsReady}
        >
          <div className="btn-capture-inner" />
        </button>

        {!ttsReady && <p className="tts-waiting">Initializing voice…</p>}
      </div>

      {showLang && (
        <LanguageSelector
          selected={selectedLanguage}
          onSelect={onLanguageChange}
          onClose={() => setShowLang(false)}
        />
      )}

      {showSettings && (
        <VoiceSettings
          volume={volume}
          speechRate={speechRate}
          onVolumeChange={onVolumeChange}
          onSpeechRateChange={onSpeechRateChange}
          onClose={() => setShowSettings(false)}
          onTest={onTestVoice}
        />
      )}
    </div>
  );
}
