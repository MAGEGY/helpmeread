// Generates PWA icons (green rounded square + white "eye" glyph) without deps.
// Usage: node scripts/gen-icons.cjs
const zlib = require('zlib');
const fs = require('fs');
const path = require('path');

// --- minimal PNG encoder (RGBA8) ---
const crcTable = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
function png(size, px) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; ihdr[9] = 6; // 8-bit RGBA
  const raw = Buffer.alloc(size * (size * 4 + 1));
  for (let y = 0; y < size; y++) {
    raw[y * (size * 4 + 1)] = 0; // filter: none
    px.copy(raw, y * (size * 4 + 1) + 1, y * size * 4, (y + 1) * size * 4);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// --- draw: green rounded square, white eye (ring + pupil) ---
function drawIcon(size, { maskable = false } = {}) {
  const px = Buffer.alloc(size * size * 4);
  const pad = maskable ? size * 0.1 : 0;          // safe zone for maskable
  const corner = size * 0.18;                      // rounded corner radius
  const cx = size / 2, cy = size / 2;
  const ringOuter = size * 0.30, ringInner = size * 0.22;
  const pupil = size * 0.12;
  const G = [0x4c, 0xaf, 0x50], W = [0xff, 0xff, 0xff], T = [0, 0, 0, 0];

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let c = T;
      // rounded-rect mask
      const inX = x >= pad && x < size - pad;
      const inY = y >= pad && y < size - pad;
      const rx = Math.min(x - pad, size - pad - 1 - x);
      const ry = Math.min(y - pad, size - pad - 1 - y);
      const inRect = inX && inY && (rx >= corner || ry >= corner ||
        Math.hypot(corner - rx, corner - ry) <= corner);
      if (inRect) {
        c = G;
        const d = Math.hypot(x - cx, y - cy);
        if ((d <= ringOuter && d >= ringInner) || d <= pupil) c = W;
      }
      const i = (y * size + x) * 4;
      px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = c[3] ?? 255;
    }
  }
  return png(size, px);
}

const outDir = path.join(__dirname, '..', 'public', 'icons');
fs.mkdirSync(outDir, { recursive: true });
for (const s of [192, 512]) {
  fs.writeFileSync(path.join(outDir, `icon-${s}.png`), drawIcon(s));
  fs.writeFileSync(path.join(outDir, `icon-maskable-${s}.png`), drawIcon(s, { maskable: true }));
}
console.log('Icons written to', outDir);
