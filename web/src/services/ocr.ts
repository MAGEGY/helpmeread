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
  const { data } = await worker.recognize(image);

  const width =
    (image as HTMLImageElement).naturalWidth ??
    (image as HTMLCanvasElement).width ??
    0;
  const height =
    (image as HTMLImageElement).naturalHeight ??
    (image as HTMLCanvasElement).height ??
    0;

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
        bbox: { x0: 0, y0: 0, x1: width, y1: height },
        confidence: (data as any).confidence ?? 0,
      },
    ];
  }

  return { text, blocks, width, height };
}
