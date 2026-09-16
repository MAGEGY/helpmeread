import { useEffect, useRef, useState } from 'react';
import './App.css';
import { CameraScreen } from './components/CameraScreen';
import { HistoryScreen } from './components/HistoryScreen';
import { ResultScreen } from './components/ResultScreen';
import { SplashScreen } from './components/SplashScreen';
import { HistoryService } from './services/history';
import { DEFAULT_LANGUAGE, type Language } from './services/languages';
import { recognizeText, type OcrBlock } from './services/ocr';
import { detectLanguage, translateText } from './services/translation';
import { TtsService } from './services/tts';

type Screen = 'splash' | 'camera' | 'processing' | 'result' | 'error' | 'history';

export default function App() {
  const [screen, setScreen] = useState<Screen>('splash');
  const [errorMsg, setErrorMsg] = useState('');
  const [selectedLang, setSelectedLang] = useState<Language>(DEFAULT_LANGUAGE);
  const [volume, setVolume] = useState(1);
  const [speechRate, setSpeechRate] = useState(1);
  const [ttsReady, setTtsReady] = useState(false);

  // Result state
  const [imageUrl, setImageUrl] = useState('');
  const [ocrBlocks, setOcrBlocks] = useState<OcrBlock[]>([]);
  const [imgW, setImgW] = useState(0);
  const [imgH, setImgH] = useState(0);
  const [speakingIdx, setSpeakingIdx] = useState<number | null>(null);
  const speakAllCancel = useRef(false);
  // Splash → camera after 2s
  useEffect(() => {
    const t = setTimeout(() => setScreen('camera'), 2000);
    return () => clearTimeout(t);
  }, []);

  // Check TTS availability
  useEffect(() => {
    setTtsReady(TtsService.isAvailable());
  }, []);

  // Speak text (translating into the selected language when the detected
  // source language differs)
  const speak = async (text: string, onDone?: () => void) => {
    if (!text) return;
    let toSpeak = text;
    const sourceLang = detectLanguage(text);
    if (sourceLang !== selectedLang.code) {
      const result = await translateText(text, sourceLang, selectedLang.code);
      if (result.translatedText) toSpeak = result.translatedText;
    }
    TtsService.speak(toSpeak, selectedLang, {
      volume,
      rate: speechRate,
      onEnd: () => {
        setSpeakingIdx(null);
        onDone?.();
      },
      onError: () => {
        setSpeakingIdx(null);
        onDone?.();
      },
    });
  };

  const handleSpeak = (text: string) => {
    // Tapping a block interrupts any auto-read in progress
    speakAllCancel.current = true;
    speak(text);
  };

  const handleSpeakAll = (blocks: OcrBlock[], onDone: () => void) => {
    speakAllCancel.current = false;
    setSpeakingIdx(0);
    const readNext = async (i: number) => {
      if (speakAllCancel.current || i >= blocks.length) {
        setSpeakingIdx(null);
        onDone();
        return;
      }
      setSpeakingIdx(i);
      speak(blocks[i].text, () => {
        setTimeout(() => readNext(i + 1), 300);
      });
    };
    readNext(0);
  };

  const handleStop = () => {
    speakAllCancel.current = true;
    TtsService.stop();
    setSpeakingIdx(null);
  };

  const handleCapture = async (file: File) => {
    setScreen('processing');
    const url = URL.createObjectURL(file);
    setImageUrl(url);

    try {
      // Load image to get dimensions
      const img = new Image();
      img.src = url;
      await new Promise((res) => (img.onload = res));
      setImgW(img.naturalWidth);
      setImgH(img.naturalHeight);

      const result = await recognizeText(img, selectedLang);
      setOcrBlocks(result.blocks);
      if (result.width && result.height) {
        setImgW(result.width);
        setImgH(result.height);
      }

      // Announce results
      const count = result.blocks.length;
      if (count > 0) {
        speak(`Found ${count} text ${count === 1 ? 'area' : 'areas'}`);
      } else {
        speak('No text found');
      }

      setScreen('result');
    } catch (e: any) {
      setErrorMsg(e?.message ?? 'Failed to process image');
      setScreen('error');
    }
  };

  const handleSave = (text: string, blockCount: number) => {
    HistoryService.save(text, selectedLang.code, blockCount);
  };

  const handleTestVoice = () => {
    const testText: Record<string, string> = {
      en: 'Hello! This is a voice test.',
      ar: 'مرحبا! هذا اختبار الصوت.',
      fr: "Bonjour! C'est un test vocal.",
      es: '¡Hola! Esta es una prueba de voz.',
      tr: 'Merhaba! Bu bir ses testidir.',
    };
    speak(testText[selectedLang.code] ?? 'Hello! This is a voice test.');
  };

  const handleBack = () => {
    handleStop();
    setOcrBlocks([]);
    if (imageUrl) URL.revokeObjectURL(imageUrl);
    setImageUrl('');
    setScreen('camera');
  };

  if (screen === 'splash') return <SplashScreen />;

  if (screen === 'error') {
    return (
      <div className="screen error-screen">
        <div className="error-icon">⚠️</div>
        <h1 className="error-title">Oops!</h1>
        <p className="error-msg">{errorMsg}</p>
        <button className="btn-retry" onClick={handleBack}>Try Again</button>
      </div>
    );
  }

  if (screen === 'processing') {
    return (
      <div className="screen processing-screen">
        <div className="processing-spinner" />
        <p className="processing-text">Reading text…</p>
      </div>
    );
  }

  if (screen === 'history') {
    return (
      <HistoryScreen
        onBack={() => setScreen('camera')}
        onReadAloud={(text) => speak(text)}
      />
    );
  }

  if (screen === 'result') {
    return (
      <ResultScreen
        imageUrl={imageUrl}
        blocks={ocrBlocks}
        imageWidth={imgW}
        imageHeight={imgH}
        selectedLanguage={selectedLang}
        volume={volume}
        speechRate={speechRate}
        onLanguageChange={setSelectedLang}
        onVolumeChange={setVolume}
        onSpeechRateChange={setSpeechRate}
        onTestVoice={handleTestVoice}
        onBack={handleBack}
        onSpeak={handleSpeak}
        onSpeakAll={handleSpeakAll}
        onStop={handleStop}
        onSave={handleSave}
        onShowHistory={() => {
          handleStop();
          setScreen('history');
        }}
        speakingIdx={speakingIdx}
      />
    );
  }

  return (
    <CameraScreen
      selectedLanguage={selectedLang}
      onLanguageChange={setSelectedLang}
      volume={volume}
      speechRate={speechRate}
      onVolumeChange={setVolume}
      onSpeechRateChange={setSpeechRate}
      onTestVoice={handleTestVoice}
      onCapture={handleCapture}
      onShowHistory={() => setScreen('history')}
      ttsReady={ttsReady}
    />
  );
}
