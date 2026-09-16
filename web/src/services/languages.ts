// Language definitions — mirrors the Android app's Language.kt

export interface Language {
  code: string;
  name: string;
  flag: string;
  tessCode: string;   // Tesseract.js OCR language code
  ttsLocale: string;  // BCP-47 for Web Speech API
}

export const SUPPORTED_LANGUAGES: Language[] = [
  { code: 'en', name: 'English',    flag: '🇬🇧', tessCode: 'eng',     ttsLocale: 'en-GB' },
  { code: 'ar', name: 'العربية',    flag: '🇸🇦', tessCode: 'ara',     ttsLocale: 'ar-SA' },
  { code: 'fr', name: 'Français',   flag: '🇫🇷', tessCode: 'fra',     ttsLocale: 'fr-FR' },
  { code: 'es', name: 'Español',    flag: '🇪🇸', tessCode: 'spa',     ttsLocale: 'es-ES' },
  { code: 'de', name: 'Deutsch',    flag: '🇩🇪', tessCode: 'deu',     ttsLocale: 'de-DE' },
  { code: 'hi', name: 'हिन्दी',     flag: '🇮🇳', tessCode: 'hin',     ttsLocale: 'hi-IN' },
  { code: 'zh', name: '中文',       flag: '🇨🇳', tessCode: 'chi_sim', ttsLocale: 'zh-CN' },
  { code: 'ja', name: '日本語',     flag: '🇯🇵', tessCode: 'jpn',     ttsLocale: 'ja-JP' },
  { code: 'ko', name: '한국어',     flag: '🇰🇷', tessCode: 'kor',     ttsLocale: 'ko-KR' },
  { code: 'ru', name: 'Русский',    flag: '🇷🇺', tessCode: 'rus',     ttsLocale: 'ru-RU' },
  { code: 'it', name: 'Italiano',   flag: '🇮🇹', tessCode: 'ita',     ttsLocale: 'it-IT' },
  { code: 'pt', name: 'Português',  flag: '🇧🇷', tessCode: 'por',     ttsLocale: 'pt-BR' },
  { code: 'tr', name: 'Türkçe',     flag: '🇹🇷', tessCode: 'tur',     ttsLocale: 'tr-TR' },
  { code: 'ur', name: 'اردو',       flag: '🇵🇰', tessCode: 'urd',     ttsLocale: 'ur-PK' },
  { code: 'bn', name: 'বাংলা',      flag: '🇧🇩', tessCode: 'ben',     ttsLocale: 'bn-BD' },
];

export const DEFAULT_LANGUAGE = SUPPORTED_LANGUAGES[0]; // English

export function getLanguage(code: string): Language {
  return SUPPORTED_LANGUAGES.find((l) => l.code === code) ?? DEFAULT_LANGUAGE;
}
