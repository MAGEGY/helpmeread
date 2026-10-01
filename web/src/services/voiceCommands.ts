// Voice command service using the Web Speech API (SpeechRecognition)
// Listens for commands: "read", "stop", "next", "back", "save", "camera", "history"
// Keywords match English + Arabic + French + Spanish (mirrors the Android
// VoiceCommandManager — the app's default audience speaks Arabic).

export const VoiceCommand = {
  READ: 'read',
  STOP: 'stop',
  NEXT: 'next',
  BACK: 'back',
  SAVE: 'save',
  CAMERA: 'camera',
  HISTORY: 'history',
  UNKNOWN: 'unknown',
} as const;

export type VoiceCommand = (typeof VoiceCommand)[keyof typeof VoiceCommand];

// TypeScript doesn't have types for SpeechRecognition, so we declare minimal ones
interface SpeechRecognitionEventLike {
  results: {
    length: number;
    [index: number]: { 0: { transcript: string } };
  };
}

export class VoiceCommandService {
  private recognition: any = null;
  private listening = false;
  private onCommand: ((cmd: VoiceCommand) => void) | null = null;

  isSupported(): boolean {
    return (
      typeof window !== 'undefined' &&
      ('SpeechRecognition' in window || 'webkitSpeechRecognition' in window)
    );
  }

  start(onCommand: (cmd: VoiceCommand) => void, lang?: string): void {
    if (!this.isSupported()) return;
    if (this.listening) return; // don't stack recognitions
    this.onCommand = onCommand;

    const SpeechRecognitionClass =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    this.recognition = new SpeechRecognitionClass();
    this.recognition.continuous = true;
    this.recognition.interimResults = false;
    this.recognition.maxAlternatives = 1;
    if (lang) this.recognition.lang = lang;

    this.recognition.onresult = (event: SpeechRecognitionEventLike) => {
      const transcript = event.results[event.results.length - 1][0].transcript
        .toLowerCase()
        .trim();
      this.onCommand?.(this.parseCommand(transcript));
    };

    this.recognition.onerror = (e: any) => {
      // Fatal errors — stop instead of restarting forever
      if (
        e?.error === 'not-allowed' ||
        e?.error === 'service-not-allowed' ||
        e?.error === 'audio-capture'
      ) {
        this.listening = false;
        return;
      }
      // Transient errors (no-speech, network) — restart
      if (this.listening) {
        setTimeout(() => this.restart(), 300);
      }
    };

    this.recognition.onend = () => {
      if (this.listening) {
        setTimeout(() => this.restart(), 100);
      }
    };

    this.listening = true;
    this.recognition.start();
  }

  private restart(): void {
    try {
      this.recognition?.start();
    } catch {
      // already started
    }
  }

  stop(): void {
    this.listening = false;
    try {
      this.recognition?.stop();
    } catch {}
  }

  private parseCommand(text: string): VoiceCommand {
    if (
      text.includes('read') || text.includes('play') || text.includes('start') ||
      text.includes('اقرأ') || text.includes('lis') || text.includes('lire') ||
      text.includes('lee') || text.includes('leer')
    ) return VoiceCommand.READ;
    if (
      text.includes('stop') || text.includes('pause') || text.includes('halt') ||
      text.includes('توقف') || text.includes('قف') || text.includes('arrêt') ||
      text.includes('para') || text.includes('detente')
    ) return VoiceCommand.STOP;
    if (
      text.includes('next') || text.includes('skip') ||
      text.includes('التالي') || text.includes('suivant') || text.includes('siguiente')
    ) return VoiceCommand.NEXT;
    if (
      text.includes('back') || text.includes('previous') || text.includes('return') ||
      text.includes('رجوع') || text.includes('retour') || text.includes('atrás')
    ) return VoiceCommand.BACK;
    if (
      text.includes('save') || text.includes('keep') || text.includes('store') ||
      text.includes('احفظ') || text.includes('enregistre') || text.includes('guarda')
    ) return VoiceCommand.SAVE;
    if (
      text.includes('camera') || text.includes('retake') || text.includes('new') ||
      text.includes('كاميرا') || text.includes('caméra') || text.includes('cámara')
    ) return VoiceCommand.CAMERA;
    if (
      text.includes('history') || text.includes('saved') || text.includes('past') ||
      text.includes('سجل') || text.includes('historique') || text.includes('historial')
    ) return VoiceCommand.HISTORY;
    return VoiceCommand.UNKNOWN;
  }
}
