// Voice command service using the Web Speech API (SpeechRecognition)
// Listens for commands: "read", "stop", "next", "back", "save", "camera", "history"

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

  start(onCommand: (cmd: VoiceCommand) => void): void {
    if (!this.isSupported()) return;
    this.onCommand = onCommand;

    const SpeechRecognitionClass =
      (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    this.recognition = new SpeechRecognitionClass();
    this.recognition.continuous = true;
    this.recognition.interimResults = false;
    this.recognition.maxAlternatives = 1;

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
    if (text.includes('read') || text.includes('play') || text.includes('start'))
      return VoiceCommand.READ;
    if (text.includes('stop') || text.includes('pause') || text.includes('halt'))
      return VoiceCommand.STOP;
    if (text.includes('next') || text.includes('skip'))
      return VoiceCommand.NEXT;
    if (text.includes('back') || text.includes('previous') || text.includes('return'))
      return VoiceCommand.BACK;
    if (text.includes('save') || text.includes('keep') || text.includes('store'))
      return VoiceCommand.SAVE;
    if (text.includes('camera') || text.includes('retake') || text.includes('new'))
      return VoiceCommand.CAMERA;
    if (text.includes('history') || text.includes('saved') || text.includes('past'))
      return VoiceCommand.HISTORY;
    return VoiceCommand.UNKNOWN;
  }
}
