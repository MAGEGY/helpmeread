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

function getWorker(tessCode: string): Promise<Tesseract.Worker> {
  let w = workers.get(tessCode);
  if (!w) {
    w = Tesseract.createWorker(tessCode, 1, {
      logger: () => {}, // suppress progress spam
    });
    workers.set(tessCode, w);
    // If creation fails, evict so the next call retries
    w.catch(() => workers.delete(tessCode));
  }
  return w;
}

/**
 * Run OCR on an image using Tesseract.js.
 * Downloads language models on first use (cached by the browser and reused
 * across captures for the same language).
 */
export async function recognizeText(
  image: HTMLImageElement | HTMLCanvasElement | string,
  language: Language
): Promise<OcrResult> {
  const worker = await getWorker(language.tessCode);
  const { data } = await worker.recognize(image);

  const blocks: OcrBlock[] = (data.blocks ?? [])
    .map((b: any) => ({
      text: b.text.trim(),
      bbox: b.bbox,
      confidence: b.confidence,
    }))
    .filter((b: OcrBlock) => b.text.length > 0);

  return {
    text: data.text.trim(),
    blocks,
    width: 0, // dimensions come from the image element, not Tesseract
    height: 0,
  };
}
