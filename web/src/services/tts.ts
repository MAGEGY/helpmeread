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

    // Try to find a matching voice
    const matchingVoice = voices.find(
      (v) => v.lang === language.ttsLocale || v.lang.startsWith(language.code)
    );
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
