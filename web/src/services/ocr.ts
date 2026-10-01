// OCR service using Tesseract.js — runs entirely in the browser, no API key

import Tesseract from 'tesseract.js';
import type { Language } from './languages';

export interface OcrBlock {
  text: string;
  bbox: { x0: number; y0: number; x1: number; y1: number };
  confidence: number;
}

export interface OcrResult {
  text: string;
  blocks: OcrBlock[];
  width: number;
  height: number;
}

// Workers are expensive to create (model download + WASM init), so cache per language
const workers = new Map<string, Promise<Tesseract.Worker>>();

function getWorker(tessCodes: string[]): Promise<Tesseract.Worker> {
  const key = tessCodes.join('+');
  let w = workers.get(key);
  if (!w) {
    w = Tesseract.createWorker(tessCodes, 1, {
      logger: () => {}, // suppress progress spam
    });
    workers.set(key, w);
    // If creation fails, evict so the next call retries
    w.catch(() => workers.delete(key));
  }
  return w;
}

/** Normalize a string URL into a loaded image element. */
async function toSource(
  image: HTMLImageElement | HTMLCanvasElement | string
): Promise<{ src: CanvasImageSource; w: number; h: number }> {
  if (typeof image === 'string') {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const el = new Image();
      el.onload = () => resolve(el);
      el.onerror = () => reject(new Error('Unsupported image format'));
      el.src = image;
    });
    return { src: img, w: img.naturalWidth, h: img.naturalHeight };
  }
  return {
    src: image,
    w:
      (image as HTMLImageElement).naturalWidth ??
      (image as HTMLCanvasElement).width,
    h:
      (image as HTMLImageElement).naturalHeight ??
      (image as HTMLCanvasElement).height,
  };
}

/** Grayscale + min-max contrast stretch — noticeably improves Tesseract
 * accuracy on dim/low-contrast phone photos. Returns a new canvas; the
 * original image is untouched. */
function preprocess(src: CanvasImageSource, w: number, h: number): HTMLCanvasElement {
  const c = document.createElement('canvas');
  c.width = w;
  c.height = h;
  const ctx = c.getContext('2d')!;
  ctx.drawImage(src, 0, 0, w, h);
  try {
    const img = ctx.getImageData(0, 0, w, h);
    const d = img.data;
    const gray = new Uint8ClampedArray(w * h);
    let min = 255;
    let max = 0;
    for (let p = 0, i = 0; p < gray.length; p++, i += 4) {
      const g = (d[i] * 0.299 + d[i + 1] * 0.587 + d[i + 2] * 0.114) | 0;
      gray[p] = g;
      if (g < min) min = g;
      if (g > max) max = g;
    }
    const range = Math.max(1, max - min);
    for (let p = 0, i = 0; p < gray.length; p++, i += 4) {
      const v = (((gray[p] - min) * 255) / range) | 0;
      d[i] = d[i + 1] = d[i + 2] = v;
    }
    ctx.putImageData(img, 0, 0);
  } catch {
    // Tainted canvas (cross-origin source) — use the unprocessed image
  }
  return c;
}

/**
 * Run OCR on an image using Tesseract.js.
 * Downloads language models on first use (cached by the browser and reused
 * across captures for the same language).
 *
 * English is always loaded alongside the chosen model: the UI language is
 * the SPEECH language, not necessarily the document language — without eng
 * a Latin-script photo under e.g. the Arabic model returns garbage.
 */
export async function recognizeText(
  image: HTMLImageElement | HTMLCanvasElement | string,
  language: Language
): Promise<OcrResult> {
  const langs = Array.from(new Set([language.tessCode, 'eng']));
  const worker = await getWorker(langs);
  const { src, w, h } = await toSource(image);
  const { data } = await worker.recognize(
    w > 0 && h > 0 ? preprocess(src, w, h) : src
  );

  const toBlocks = (items: any[] | null | undefined): OcrBlock[] =>
    (items ?? [])
      .map((b: any) => ({
        text: (b.text ?? '').trim(),
        bbox: b.bbox,
        confidence: b.confidence ?? 0,
      }))
      // Require at least one letter or digit — blocks of pure punctuation/
      // symbols are OCR noise and would be read aloud as garbage
      .filter((b: OcrBlock) => b.bbox && /[\p{L}\p{N}]/u.test(b.text));

  // Tesseract.js v7 nests geometry: blocks → paragraphs → lines → words.
  // Prefer coarse blocks; fall back to finer granularity when empty.
  const flat = (arr: any[] | null | undefined, key: string): any[] =>
    (arr ?? []).flatMap((x: any) => x[key] ?? []);
  const paragraphs = flat(data.blocks, 'paragraphs');
  const lines = flat(paragraphs, 'lines');
  const words = flat(lines, 'words');

  let blocks = toBlocks(data.blocks);
  if (blocks.length === 0) blocks = toBlocks(paragraphs);
  if (blocks.length === 0) blocks = toBlocks(lines);
  if (blocks.length === 0) blocks = toBlocks(words);

  const text = data.text.trim();

  // Last resort: Tesseract found text but returned no geometry — make the
  // whole image one tappable block so the user can still hear it
  if (blocks.length === 0 && text.length > 0) {
    blocks = [
      {
        text,
        bbox: { x0: 0, y0: 0, x1: w, y1: h },
        confidence: (data as any).confidence ?? 0,
      },
    ];
  }

  return { text, blocks, width: w, height: h };
}
