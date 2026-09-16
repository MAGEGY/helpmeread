import { useEffect, useRef, useState } from 'react';
import type { Language } from '../services/languages';
import type { OcrBlock } from '../services/ocr';
import { VoiceCommand, VoiceCommandService } from '../services/voiceCommands';
import { LanguageSelector } from './LanguageSelector';
import { VoiceSettings } from './VoiceSettings';

interface Props {
  imageUrl: string;
  blocks: OcrBlock[];
  imageWidth: number;
  imageHeight: number;
  selectedLanguage: Language;
  volume: number;
  speechRate: number;
  onLanguageChange: (lang: Language) => void;
  onVolumeChange: (v: number) => void;
  onSpeechRateChange: (r: number) => void;
  onTestVoice: () => void;
  onBack: () => void;
  onSpeak: (text: string) => void;
  onSpeakAll: (blocks: OcrBlock[], onDone: () => void) => void;
  onStop: () => void;
  onSave: (text: string, blockCount: number) => void;
  onShowHistory: () => void;
  speakingIdx: number | null;
}

export function ResultScreen({
  imageUrl,
  blocks,
  imageWidth,
  imageHeight,
  selectedLanguage,
  volume,
  speechRate,
  onLanguageChange,
  onVolumeChange,
  onSpeechRateChange,
  onTestVoice,
  onBack,
  onSpeak,
  onSpeakAll,
  onStop,
  onSave,
  onShowHistory,
  speakingIdx,
}: Props) {
  const [selectedIdx, setSelectedIdx] = useState<number | null>(null);
  const [isAutoReading, setIsAutoReading] = useState(false);
  const [showLang, setShowLang] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [isSaved, setIsSaved] = useState(false);
  const [isListening, setIsListening] = useState(false);
  const [displaySize, setDisplaySize] = useState<{ w: number; h: number } | null>(null);
  const voiceCmdRef = useRef(new VoiceCommandService());
  const imgRef = useRef<HTMLImageElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const voiceCmd = voiceCmdRef.current;

  // Mirror of selectedIdx so the voice-command handler can read it without
  // restarting recognition on every selection change
  const selectedIdxRef = useRef(selectedIdx);
  selectedIdxRef.current = selectedIdx;

  // Measure the rendered image size for overlay positioning — must run after
  // layout, not during render (getBoundingClientRect is 0 before the image loads)
  const updateDisplaySize = () => {
    const img = imgRef.current;
    if (!img) return;
    const rect = img.getBoundingClientRect();
    if (rect.width > 0) setDisplaySize({ w: rect.width, h: rect.height });
  };

  useEffect(() => {
    updateDisplaySize();
    window.addEventListener('resize', updateDisplaySize);
    return () => window.removeEventListener('resize', updateDisplaySize);
  }, [imageUrl]);

  // Handle voice commands
  useEffect(() => {
    if (!isListening) return;
    const handler = (cmd: VoiceCommand) => {
      switch (cmd) {
        case VoiceCommand.READ:
          setIsAutoReading(true);
          onSpeakAll(blocks, () => setIsAutoReading(false));
          break;
        case VoiceCommand.STOP:
          onStop();
          setIsAutoReading(false);
          break;
        case VoiceCommand.SAVE:
          if (!isSaved) {
            onSave(blocks.map((b) => b.text).join('\n'), blocks.length);
            setIsSaved(true);
          }
          break;
        case VoiceCommand.NEXT: {
          if (blocks.length > 0) {
            const next = Math.min((selectedIdxRef.current ?? -1) + 1, blocks.length - 1);
            setSelectedIdx(next);
            onSpeak(blocks[next].text);
          }
          break;
        }
        case VoiceCommand.CAMERA:
        case VoiceCommand.BACK:
          onBack();
          break;
        case VoiceCommand.HISTORY:
          onShowHistory();
          break;
      }
    };
    voiceCmd.start(handler);
    return () => voiceCmd.stop();
  }, [isListening, blocks, isSaved]);

  // Handle tap on overlay
  const handleImageClick = (e: React.MouseEvent<HTMLImageElement>) => {
    const img = imgRef.current;
    if (!img || imageWidth === 0) return;
    const rect = img.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * imageWidth;
    const y = ((e.clientY - rect.top) / rect.height) * imageHeight;

    for (let i = 0; i < blocks.length; i++) {
      const b = blocks[i].bbox;
      if (x >= b.x0 && x <= b.x1 && y >= b.y0 && y <= b.y1) {
        setSelectedIdx(i);
        onSpeak(blocks[i].text);
        return;
      }
    }
  };

  return (
    <div className="screen result-screen">
      {/* Top controls */}
      <div className="result-top-bar">
        <button className="btn-icon" onClick={onBack} title="Back">←</button>
        <button className="btn-icon" onClick={() => setShowLang(true)} title="Language">
          {selectedLanguage.flag}
        </button>
        <button
          className={`btn-icon ${isListening ? 'btn-icon-active' : ''}`}
          onClick={() => setIsListening(!isListening)}
          title="Voice Commands"
        >
          {isListening ? '🔴' : '🎤'}
        </button>
        <button className="btn-icon" onClick={() => setShowSettings(true)} title="Settings">⚙️</button>
      </div>

      {/* Voice command hint */}
      {isListening && (
        <div className="voice-hint">
          🎙️ Listening… Say: Read, Stop, Save, Camera, History
        </div>
      )}

      {/* Image with overlays */}
      <div className="result-image-container" ref={containerRef}>
        <div className="result-image-wrap">
          <img
            ref={imgRef}
            src={imageUrl}
            alt="Captured"
            className="result-image"
            onClick={handleImageClick}
            onLoad={updateDisplaySize}
          />
          {/* Overlay boxes */}
          {displaySize && blocks.map((block, i) => {
            if (imageWidth === 0) return null;
            const scaleX = displaySize.w / imageWidth;
            const scaleY = displaySize.h / imageHeight;
            const b = block.bbox;
            const isSelected = selectedIdx === i;
            const isSpeaking = speakingIdx === i;
            return (
              <div
                key={i}
                className={`ocr-box ${isSelected ? 'ocr-selected' : ''} ${isSpeaking ? 'ocr-speaking' : ''}`}
                style={{
                  left: `${b.x0 * scaleX}px`,
                  top: `${b.y0 * scaleY}px`,
                  width: `${(b.x1 - b.x0) * scaleX}px`,
                  height: `${(b.y1 - b.y0) * scaleY}px`,
                }}
                onClick={(e) => {
                  e.stopPropagation();
                  setSelectedIdx(i);
                  onSpeak(block.text);
                }}
              />
            );
          })}
        </div>
      </div>

      {/* Bottom controls */}
      <div className="result-bottom-bar">
        {blocks.length > 0 ? (
          <>
            <div className="control-row">
              <button
                className={`btn-circle ${isAutoReading ? 'btn-circle-stop' : 'btn-circle-play'}`}
                onClick={() => {
                  if (isAutoReading) {
                    onStop();
                    setIsAutoReading(false);
                  } else {
                    setIsAutoReading(true);
                    onSpeakAll(blocks, () => setIsAutoReading(false));
                  }
                }}
              >
                {isAutoReading ? '⏹' : '▶'}
              </button>

              {(isAutoReading || speakingIdx !== null) && (
                <button className="btn-circle btn-circle-pause" onClick={() => { onStop(); setIsAutoReading(false); }}>
                  ⏸
                </button>
              )}

              <button
                className={`btn-circle ${isSaved ? 'btn-circle-saved' : 'btn-circle-save'}`}
                onClick={() => {
                  if (!isSaved) {
                    onSave(blocks.map((b) => b.text).join('\n'), blocks.length);
                    setIsSaved(true);
                  }
                }}
              >
                {isSaved ? '✓' : '💾'}
              </button>
            </div>
            <p className="result-count">{blocks.length} text areas found</p>
          </>
        ) : (
          <p className="result-empty">No text found</p>
        )}
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
