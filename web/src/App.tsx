import { useEffect, useRef, useState } from 'react';
import './App.css';
import { CameraScreen } from './components/CameraScreen';
import { CropScreen, type CropRect } from './components/CropScreen';
import { HistoryScreen } from './components/HistoryScreen';
import { ResultScreen } from './components/ResultScreen';
import { SplashScreen } from './components/SplashScreen';
import { HistoryService } from './services/history';
import { DEFAULT_LANGUAGE, type Language } from './services/languages';
import { recognizeText, type OcrBlock } from './services/ocr';
import { detectLanguage, translateText } from './services/translation';
import { TtsService, cleanForSpeech, splitSentences } from './services/tts';

type Screen = 'splash' | 'camera' | 'crop' | 'processing' | 'result' | 'error' | 'history';

const MAX_OCR_DIM = 1600;

// Decode a File to an ImageBitmap (EXIF orientation applied) or fall back to
// an <img> element for older browsers / formats createImageBitmap rejects.
async function decodeImage(
  file: File
): Promise<{ source: CanvasImageSource; w: number; h: number }> {
  try {
    const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' });
    return { source: bmp, w: bmp.width, h: bmp.height };
  } catch {
    const url = URL.createObjectURL(file);
    try {
      const img = await new Promise<HTMLImageElement>((resolve, reject) => {
        const el = new Image();
        el.onload = () => resolve(el);
        el.onerror = () => reject(new Error('Unsupported image format'));
        el.src = url;
      });
      return { source: img, w: img.naturalWidth, h: img.naturalHeight };
    } finally {
      URL.revokeObjectURL(url);
    }
  }
}

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
  const [pendingCanvas, setPendingCanvas] = useState<HTMLCanvasElement | null>(null);
  // Splash → camera after 2s
  useEffect(() => {
    const t = setTimeout(() => setScreen('camera'), 2000);
    return () => clearTimeout(t);
  }, []);

  // Check TTS availability
  useEffect(() => {
    setTtsReady(TtsService.isAvailable());
  }, []);

  // Clean OCR text and translate into the selected language when needed.
  const prepareSpeech = async (text: string): Promise<string> => {
    const cleaned = cleanForSpeech(text);
    if (!cleaned) return '';
    const sourceLang = detectLanguage(cleaned);
    if (sourceLang !== selectedLang.code) {
      const result = await translateText(cleaned, sourceLang, selectedLang.code);
      if (result.translatedText) return result.translatedText;
    }
    return cleaned;
  };

  const speakPrepared = (toSpeak: string, onDone?: () => void) => {
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

  const speakOnce = (toSpeak: string): Promise<void> =>
    new Promise((res) => speakPrepared(toSpeak, res));

  // Speak text (translating into the selected language when the detected
  // source language differs)
  const speak = async (text: string, onDone?: () => void) => {
    const toSpeak = await prepareSpeech(text);
    if (!toSpeak) {
      onDone?.();
      return;
    }
    speakPrepared(toSpeak, onDone);
  };

  const handleSpeak = (text: string) => {
    // Tapping a block interrupts any auto-read in progress
    speakAllCancel.current = true;
    speak(text);
  };

  const handleSpeakAll = (blocks: OcrBlock[], onDone: () => void) => {
    speakAllCancel.current = false;
    const run = async () => {
      for (let i = 0; i < blocks.length; i++) {
        if (speakAllCancel.current) break;
        // Clean + translate once per block, then read sentence by sentence
        // for a natural rhythm (blocks often end mid-sentence).
        const prepared = await prepareSpeech(blocks[i].text);
        if (!prepared) continue;
        for (const sentence of splitSentences(prepared, selectedLang.code)) {
          if (speakAllCancel.current) break;
          setSpeakingIdx(i);
          await speakOnce(sentence);
        }
      }
      setSpeakingIdx(null);
      onDone();
    };
    run();
  };

  const handleStop = () => {
    speakAllCancel.current = true;
    TtsService.stop();
    setSpeakingIdx(null);
  };

  const handleCapture = async (file: File) => {
    setScreen('processing');

    try {
      // Normalize through a canvas: applies EXIF rotation and downscales
      // huge photos so Tesseract runs reliably. The same canvas is used for
      // display + OCR, so bounding boxes always align.
      const { source, w, h } = await decodeImage(file);
      const scale = Math.min(1, MAX_OCR_DIM / Math.max(w, h));
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(w * scale));
      canvas.height = Math.max(1, Math.round(h * scale));
      canvas.getContext('2d')!.drawImage(source, 0, 0, canvas.width, canvas.height);

      setPendingCanvas(canvas);
      setImageUrl(canvas.toDataURL('image/jpeg', 0.92));
      setImgW(canvas.width);
      setImgH(canvas.height);
      setScreen('crop');
    } catch (e: any) {
      setErrorMsg(e?.message ?? 'Failed to process image');
      setScreen('error');
    }
  };

  // Run OCR on a (possibly cropped) canvas and show results
  const runOcr = async (canvas: HTMLCanvasElement) => {
    setScreen('processing');
    try {
      setImageUrl(canvas.toDataURL('image/jpeg', 0.92));
      setImgW(canvas.width);
      setImgH(canvas.height);

      const result = await recognizeText(canvas, selectedLang);
      setOcrBlocks(result.blocks);

      const count = result.blocks.length;
      speak(
        count > 0
          ? `Found ${count} text ${count === 1 ? 'area' : 'areas'}`
          : 'No text found'
      );

      setScreen('result');
    } catch (e: any) {
      setErrorMsg(e?.message ?? 'Failed to process image');
      setScreen('error');
    }
  };

  const handleCrop = (rect: CropRect | null) => {
    const src = pendingCanvas;
    if (!src) return;
    if (!rect) {
      runOcr(src); // whole image
      return;
    }
    const w = Math.max(1, Math.min(rect.x1, src.width) - Math.max(0, rect.x0));
    const h = Math.max(1, Math.min(rect.y1, src.height) - Math.max(0, rect.y0));
    const c = document.createElement('canvas');
    c.width = w;
    c.height = h;
    c.getContext('2d')!.drawImage(src, rect.x0, rect.y0, w, h, 0, 0, w, h);
    runOcr(c);
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
    setPendingCanvas(null);
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

  if (screen === 'crop') {
    return (
      <CropScreen
        imageUrl={imageUrl}
        imageWidth={imgW}
        onCrop={handleCrop}
        onBack={handleBack}
      />
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
