import { useEffect, useRef, useState } from 'react';
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
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [showLang, setShowLang] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [flashOn, setFlashOn] = useState(false);
  const [camError, setCamError] = useState<string | null>(null);
  const [flashSupported, setFlashSupported] = useState<boolean | null>(null);

  // Live camera viewfinder (getUserMedia). Falls back to upload when the
  // camera is unavailable or permission is denied.
  useEffect(() => {
    let cancelled = false;
    if (!navigator.mediaDevices?.getUserMedia) {
      setCamError('Camera needs HTTPS — use the gallery button to upload');
      return;
    }
    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: 'environment' }, audio: false })
      .then((stream) => {
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }
        streamRef.current = stream;
        const v = videoRef.current;
        if (v) {
          v.srcObject = stream;
          v.onloadedmetadata = () => v.play().catch(() => {});
        }
        // Real torch only where the device reports the capability
        const caps = (stream.getVideoTracks()[0] as any)?.getCapabilities?.();
        setFlashSupported(!!caps?.torch);
      })
      .catch((err: any) => {
        if (cancelled) return;
        setCamError(
          err?.name === 'NotAllowedError'
            ? 'Camera permission denied — allow it in browser settings, or use gallery'
            : 'Camera unavailable — use the gallery button to upload a photo'
        );
      });
    return () => {
      cancelled = true;
      streamRef.current?.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    };
  }, []);

  const captureFrame = () => {
    const v = videoRef.current;
    if (!v || !v.videoWidth) {
      // Camera not delivering frames — fall back to the file picker
      fileInputRef.current?.click();
      return;
    }
    const c = document.createElement('canvas');
    c.width = v.videoWidth;
    c.height = v.videoHeight;
    c.getContext('2d')!.drawImage(v, 0, 0);
    c.toBlob(
      (blob) => {
        if (blob) onCapture(new File([blob], 'capture.jpg', { type: 'image/jpeg' }));
        else fileInputRef.current?.click();
      },
      'image/jpeg',
      0.92
    );
  };

  const toggleFlash = () => {
    const track = streamRef.current?.getVideoTracks()[0];
    const next = !flashOn;
    // No-op when the device has no torch — the button is disabled in that case
    if (!track || flashSupported === false) return;
    (track as any)
      .applyConstraints({ advanced: [{ torch: next }] })
      .then(() => setFlashOn(next))
      .catch(() => setFlashOn(false));
  };

  const handleFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) onCapture(file);
    // Reset so the same file can be selected again
    e.target.value = '';
  };

  return (
    <div className="screen camera-screen">
      {/* Live viewfinder behind controls */}
      <video
        ref={videoRef}
        className="camera-video"
        autoPlay
        muted
        playsInline
      />

      {/* Top hint */}
      <div className="camera-hint">
        {camError ?? '📷 Point camera at text — or upload an image'}
      </div>

      {/* History button */}
      <button className="btn-icon btn-top-left" onClick={onShowHistory} title="History">
        🕐
      </button>

      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        onChange={handleFile}
        style={{ display: 'none' }}
      />

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
            className={`btn-circle ${flashOn ? 'btn-circle-flash' : ''} ${flashSupported === false ? 'btn-disabled' : ''}`}
            onClick={toggleFlash}
            disabled={flashSupported === false}
            title={flashSupported === false ? 'Flash not available on this device' : 'Flash'}
          >
            {flashOn ? '⚡' : '🔦'}
          </button>

          <button
            className="btn-circle"
            onClick={() => fileInputRef.current?.click()}
            title="Upload from gallery"
          >
            🖼️
          </button>

          <button
            className="btn-circle"
            onClick={() => setShowSettings(true)}
            title="Voice Settings"
          >
            🔊
          </button>
        </div>

        <button className="btn-capture" onClick={captureFrame} title="Capture">
          <div className="btn-capture-inner" />
        </button>

        {!ttsReady && <p className="tts-waiting">Voice not ready — capture still works</p>}
        {camError && <p className="tts-waiting">Camera off — tap 🖼️ to upload</p>}
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
