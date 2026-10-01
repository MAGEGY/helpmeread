import { useEffect, useRef, useState } from 'react';

export interface CropRect {
  x0: number;
  y0: number;
  x1: number;
  y1: number;
}

interface Props {
  imageUrl: string;
  imageWidth: number;
  onCrop: (rect: CropRect | null) => void; // null = whole image
  onBack: () => void;
}

interface DRect {
  x: number;
  y: number;
  w: number;
  h: number;
}

type Mode = 'move' | 'nw' | 'ne' | 'sw' | 'se' | 'new' | null;

const MIN = 24; // min selection size in displayed px
const HANDLE = 30; // handle hit area in px

export function CropScreen({ imageUrl, imageWidth, onCrop, onBack }: Props) {
  const imgRef = useRef<HTMLImageElement>(null);
  const wrapRef = useRef<HTMLDivElement>(null);
  const [disp, setDisp] = useState<{ w: number; h: number } | null>(null);
  const [rect, setRect] = useState<DRect | null>(null);
  const dragRef = useRef<{ mode: Mode; sx: number; sy: number; orig: DRect } | null>(null);

  // Measure the rendered image — selection coordinates live in displayed px
  // and are converted to image coordinates on confirm.
  const measure = () => {
    const r = imgRef.current?.getBoundingClientRect();
    if (r && r.width > 0) {
      setDisp({ w: r.width, h: r.height });
      setRect((prev) => prev ?? { x: 0, y: 0, w: r.width, h: r.height });
    }
  };

  useEffect(() => {
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, []);

  const local = (e: React.PointerEvent) => {
    const wrap = wrapRef.current!.getBoundingClientRect();
    return { px: e.clientX - wrap.left, py: e.clientY - wrap.top };
  };

  const hitHandle = (px: number, py: number, r: DRect): Mode => {
    const near = (hx: number, hy: number) =>
      Math.abs(px - hx) <= HANDLE / 2 && Math.abs(py - hy) <= HANDLE / 2;
    if (near(r.x, r.y)) return 'nw';
    if (near(r.x + r.w, r.y)) return 'ne';
    if (near(r.x, r.y + r.h)) return 'sw';
    if (near(r.x + r.w, r.y + r.h)) return 'se';
    return null;
  };

  const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(v, hi));

  const applyDrag = (mode: Mode, o: DRect, dx: number, dy: number, W: number, H: number): DRect => {
    let l = o.x, t = o.y, r = o.x + o.w, b = o.y + o.h;
    if (mode === 'move') {
      l += dx; r += dx; t += dy; b += dy;
      if (l < 0) { r -= l; l = 0; }
      if (r > W) { l -= r - W; r = W; }
      if (t < 0) { b -= t; t = 0; }
      if (b > H) { t -= b - H; b = H; }
    } else {
      if (mode === 'nw' || mode === 'sw') l = clamp(l + dx, 0, r - MIN);
      if (mode === 'ne' || mode === 'se') r = clamp(r + dx, l + MIN, W);
      if (mode === 'nw' || mode === 'ne') t = clamp(t + dy, 0, b - MIN);
      if (mode === 'sw' || mode === 'se') b = clamp(b + dy, t + MIN, H);
    }
    return { x: l, y: t, w: r - l, h: b - t };
  };

  const onPointerDown = (e: React.PointerEvent) => {
    if (!disp) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    const { px, py } = local(e);
    if (rect) {
      const h = hitHandle(px, py, rect);
      if (h) {
        dragRef.current = { mode: h, sx: px, sy: py, orig: rect };
        return;
      }
      if (px >= rect.x && px <= rect.x + rect.w && py >= rect.y && py <= rect.y + rect.h) {
        dragRef.current = { mode: 'move', sx: px, sy: py, orig: rect };
        return;
      }
    }
    // Tap outside the rect starts a fresh selection
    const orig = { x: px, y: py, w: 0, h: 0 };
    dragRef.current = { mode: 'new', sx: px, sy: py, orig };
    setRect(orig);
  };

  const onPointerMove = (e: React.PointerEvent) => {
    const d = dragRef.current;
    if (!d || !disp) return;
    const { px, py } = local(e);
    if (d.mode === 'new') {
      const l = clamp(Math.min(d.sx, px), 0, disp.w);
      const r = clamp(Math.max(d.sx, px), 0, disp.w);
      const t = clamp(Math.min(d.sy, py), 0, disp.h);
      const b = clamp(Math.max(d.sy, py), 0, disp.h);
      setRect({ x: l, y: t, w: r - l, h: b - t });
    } else {
      setRect(applyDrag(d.mode, d.orig, px - d.sx, py - d.sy, disp.w, disp.h));
    }
  };

  const onPointerUp = () => {
    const d = dragRef.current;
    dragRef.current = null;
    // Discard accidental tiny drags
    if (d?.mode === 'new' && rect && (rect.w < MIN || rect.h < MIN)) {
      setRect({ x: 0, y: 0, w: disp!.w, h: disp!.h });
    }
  };

  const confirm = () => {
    if (!rect || !disp || rect.w >= disp.w - 1 && rect.h >= disp.h - 1) {
      onCrop(null); // whole image
      return;
    }
    const s = imageWidth / disp.w;
    onCrop({
      x0: Math.round(rect.x * s),
      y0: Math.round(rect.y * s),
      x1: Math.round((rect.x + rect.w) * s),
      y1: Math.round((rect.y + rect.h) * s),
    });
  };

  return (
    <div className="screen result-screen">
      <div className="result-top-bar">
        <button className="btn-icon" onClick={onBack} title="Back">←</button>
        <span className="crop-title">Select text area</span>
        <span style={{ width: 40 }} />
      </div>

      <div className="result-image-container">
        <div className="result-image-wrap" ref={wrapRef}>
          <img
            ref={imgRef}
            src={imageUrl}
            alt="Captured"
            className="result-image"
            onLoad={measure}
            draggable={false}
          />
          {disp && rect && (
            <div
              className="crop-overlay"
              onPointerDown={onPointerDown}
              onPointerMove={onPointerMove}
              onPointerUp={onPointerUp}
              onPointerCancel={onPointerUp}
            >
              <div
                className="crop-rect"
                style={{
                  left: rect.x,
                  top: rect.y,
                  width: rect.w,
                  height: rect.h,
                }}
              >
                <div className="crop-handle" style={{ left: 0, top: 0 }} />
                <div className="crop-handle" style={{ left: '100%', top: 0 }} />
                <div className="crop-handle" style={{ left: 0, top: '100%' }} />
                <div className="crop-handle" style={{ left: '100%', top: '100%' }} />
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="result-bottom-bar">
        <div className="control-row">
          <button className="btn-circle btn-circle-play" onClick={confirm} title="Scan selection">
            ✂️
          </button>
          <button className="btn-circle" onClick={() => onCrop(null)} title="Scan whole image">
            📄
          </button>
        </div>
        <p className="result-count">Drag to select • corners to resize • ✂️ scan area • 📄 scan all</p>
      </div>
    </div>
  );
}
