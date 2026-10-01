// Text-to-Speech service using the Web Speech API (SpeechSynthesis)

import type { Language } from './languages';

let voices: SpeechSynthesisVoice[] = [];

function loadVoices(): Promise<SpeechSynthesisVoice[]> {
  return new Promise((resolve) => {
    voices = window.speechSynthesis.getVoices();
    if (voices.length > 0) {
      resolve(voices);
      return;
    }
    // Voices load asynchronously in some browsers
    window.speechSynthesis.onvoiceschanged = () => {
      voices = window.speechSynthesis.getVoices();
      resolve(voices);
    };
    // Fallback timeout
    setTimeout(() => resolve(window.speechSynthesis.getVoices()), 1000);
  });
}

let voicesReady = loadVoices();

/** Remove OCR/symbol noise so the voice reads like a human — keeps letters,
 * digits, currency, apostrophes and normal sentence punctuation. */
export function cleanForSpeech(text: string): string {
  return text
    // decorative symbols / OCR junk (bullets, arrows, shapes, stray quotes)
    .replace(/[•·‣◦▪▫●○■□▲△►◄▼▽♦◆★☆✓✔✗✘→←↑↓↔↕«»‹›„“”‘’"~^_|\\<>{}[\]©®™°§¶†‡]/g, ' ')
    // runs of punctuation (OCR noise like "..." "---" "؟؟؟")
    .replace(/[.,;:!?؟…\-–—]{2,}/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Split into sentences for natural reading rhythm. Uses Intl.Segmenter
 * where available, with a punctuation fallback. */
export function splitSentences(text: string, lang: string): string[] {
  if (typeof (Intl as any).Segmenter === 'function') {
    const seg = new Intl.Segmenter(lang, { granularity: 'sentence' });
    return Array.from(seg.segment(text))
      .map((s) => s.segment.trim())
      .filter(Boolean);
  }
  return (
    text
      .match(/[^.!?؟…]+[.!?؟…]*/g)
      ?.map((s) => s.trim())
      .filter(Boolean) ?? [text]
  );
}

/** Pick the most natural-sounding voice for the language. Cloud/neural
 * voices (Google on Android, "Natural" on Edge) sound far less robotic. */
function pickVoice(language: Language): SpeechSynthesisVoice | null {
  const norm = (l: string) => l.replace('_', '-').toLowerCase();
  const candidates = voices.filter((v) => {
    const l = norm(v.lang);
    return l === norm(language.ttsLocale) || l.startsWith(language.code);
  });
  if (!candidates.length) return null;
  const score = (v: SpeechSynthesisVoice): number => {
    const n = v.name.toLowerCase();
    let s = 0;
    if (/(natural|neural|online)/.test(n)) s += 6;   // Edge neural voices
    if (/google/.test(n)) s += 5;                    // Chrome/Android voices
    if (!v.localService) s += 3;                     // cloud-backed = better
    if (/(compact|espeak|basic)/.test(n)) s -= 6;    // robotic fallbacks
    if (norm(v.lang) === norm(language.ttsLocale)) s += 2;
    return s;
  };
  return candidates.reduce((a, b) => (score(b) > score(a) ? b : a));
}

export interface TtsOptions {
  volume?: number;   // 0..1
  rate?: number;     // 0.5..2
  onStart?: () => void;
  onEnd?: () => void;
  onError?: (e: any) => void;
}

export const TtsService = {
  isAvailable(): boolean {
    return typeof window !== 'undefined' && 'speechSynthesis' in window;
  },

  async speak(text: string, language: Language, opts: TtsOptions = {}): Promise<void> {
    if (!this.isAvailable()) {
      opts.onError?.(new Error('Speech synthesis not supported'));
      return;
    }

    await voicesReady;
    // Refresh — voices may have loaded after the initial call resolved
    voices = window.speechSynthesis.getVoices();

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = language.ttsLocale;
    utterance.volume = opts.volume ?? 1;
    utterance.rate = opts.rate ?? 1;
    utterance.pitch = 1;

    // Try to find the most natural matching voice
    const matchingVoice = pickVoice(language);
    if (matchingVoice) {
      utterance.voice = matchingVoice;
    }

    utterance.onstart = () => opts.onStart?.();
    utterance.onend = () => opts.onEnd?.();
    utterance.onerror = (e) => {
      // If the chosen language fails, retry with default voice
      if (utterance.voice) {
        utterance.voice = null;
        utterance.lang = '';
        window.speechSynthesis.speak(utterance);
      } else {
        opts.onError?.(e);
      }
    };

    window.speechSynthesis.cancel(); // flush previous
    window.speechSynthesis.speak(utterance);
  },

  stop(): void {
    if (this.isAvailable()) {
      window.speechSynthesis.cancel();
    }
  },

  isSpeaking(): boolean {
    return this.isAvailable() && window.speechSynthesis.speaking;
  },
};
