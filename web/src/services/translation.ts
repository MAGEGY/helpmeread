// Translation service using MyMemory API (free, no key required)
// https://mymemory.translated.net/doc/spec.php

const MYMEMORY_URL = 'https://api.mymemory.translated.net/get';

// BCP-47 map for MyMemory (it expects langpair like en|ar)
const LANG_MAP: Record<string, string> = {
  en: 'en', ar: 'ar', fr: 'fr', es: 'es', de: 'de', hi: 'hi',
  zh: 'zh', ja: 'ja', ko: 'ko', ru: 'ru', it: 'it', pt: 'pt',
  tr: 'tr', ur: 'ur', bn: 'bn',
};

export interface TranslationResult {
  translatedText: string;
  error?: string;
}

/**
 * Guess the language of OCR'd text from Unicode script ranges.
 * MyMemory has no auto-detect, so we detect the dominant script client-side.
 * Ambiguous scripts fall back to a reasonable default (e.g. Urdu uses Arabic
 * script → 'ar'; kanji-only text → 'zh').
 */
export function detectLanguage(text: string): string {
  const counts: Record<string, number> = {};
  for (const ch of text) {
    const cp = ch.codePointAt(0)!;
    let s: string | null = null;
    if (cp >= 0x0600 && cp <= 0x06ff) s = 'ar';
    else if (cp >= 0x0750 && cp <= 0x077f) s = 'ar';
    else if (cp >= 0x08a0 && cp <= 0x08ff) s = 'ar';
    else if (cp >= 0xfb50 && cp <= 0xfdff) s = 'ar';
    else if (cp >= 0x0980 && cp <= 0x09ff) s = 'bn';
    else if (cp >= 0x0900 && cp <= 0x097f) s = 'hi';
    else if ((cp >= 0x3040 && cp <= 0x309f) || (cp >= 0x30a0 && cp <= 0x30ff)) s = 'ja';
    else if (cp >= 0xac00 && cp <= 0xd7af) s = 'ko';
    else if (cp >= 0x4e00 && cp <= 0x9fff) s = 'zh';
    else if (cp >= 0x0400 && cp <= 0x04ff) s = 'ru';
    else if ((cp >= 0x41 && cp <= 0x5a) || (cp >= 0x61 && cp <= 0x7a) || (cp >= 0xc0 && cp <= 0x24f)) s = 'en';
    if (s) counts[s] = (counts[s] ?? 0) + 1;
  }
  let best = 'en';
  let bestCount = 0;
  for (const [lang, n] of Object.entries(counts)) {
    if (n > bestCount) {
      best = lang;
      bestCount = n;
    }
  }
  return best;
}

export async function translateText(
  text: string,
  sourceLang: string,
  targetLang: string
): Promise<TranslationResult> {
  const trimmed = text.trim();
  if (!trimmed) return { translatedText: '' };

  const src = LANG_MAP[sourceLang] ?? sourceLang;
  const tgt = LANG_MAP[targetLang] ?? targetLang;

  if (src === tgt) return { translatedText: text };

  try {
    const url = `${MYMEMORY_URL}?q=${encodeURIComponent(trimmed)}&langpair=${src}|${tgt}`;
    const res = await fetch(url);
    const data = await res.json();

    if (data.responseStatus !== 200 && data.responseStatus !== '200') {
      return { translatedText: '', error: 'Translation quota exceeded' };
    }

    // MyMemory sometimes returns a terminology marker "[...]" for short text
    let translated = data.responseData?.translatedText ?? '';
    if (translated.trim().startsWith('[')) {
      const matches = data.matches ?? [];
      for (const m of matches) {
        const t = m.translation ?? '';
        if (t && !t.trim().startsWith('[')) {
          translated = t;
          break;
        }
      }
    }

    return { translatedText: translated.trim() };
  } catch (e: any) {
    return { translatedText: '', error: e?.message ?? 'Translation failed' };
  }
}
