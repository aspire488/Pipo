import * as THREE from 'three';
import { mulberry } from './util.js';

/* Procedural canvas textures. Everything is drawn once at load; no image files to fetch. */

if (!CanvasRenderingContext2D.prototype.roundRect) {
  CanvasRenderingContext2D.prototype.roundRect = function (x, y, w, h, r) {
    const q = Math.min(r, w / 2, h / 2);
    this.moveTo(x + q, y); this.arcTo(x + w, y, x + w, y + h, q); this.arcTo(x + w, y + h, x, y + h, q);
    this.arcTo(x, y + h, x, y, q); this.arcTo(x, y, x + w, y, q); this.closePath();
  };
}

function make(w, h, draw, { srgb = true, repeat = null, aniso = 4 } = {}) {
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  const x = c.getContext('2d');
  draw(x, w, h);
  const t = new THREE.CanvasTexture(c);
  if (srgb) t.colorSpace = THREE.SRGBColorSpace;
  if (repeat) { t.wrapS = t.wrapT = THREE.RepeatWrapping; t.repeat.set(repeat[0], repeat[1]); }
  t.anisotropy = aniso;
  return t;
}

/** Warm floorboards: staggered planks with grain, tone variation and dark seams. Greyscale-ish around white so material.color still tints. */
export function floorTexture() {
  const R = mulberry(11);
  return make(1024, 1024, (x, w, h) => {
    const rows = 16, rh = h / rows;
    for (let r = 0; r < rows; r++) {
      let px = -R() * 300;
      while (px < w) {
        const len = 260 + R() * 340;
        const tone = 0.86 + R() * 0.2;
        const g = x.createLinearGradient(0, r * rh, 0, (r + 1) * rh);
        const c1 = `rgb(${255 * tone | 0},${246 * tone | 0},${236 * tone | 0})`;
        const c2 = `rgb(${236 * tone | 0},${222 * tone | 0},${208 * tone | 0})`;
        g.addColorStop(0, c1); g.addColorStop(1, c2);
        x.fillStyle = g;
        x.fillRect(px, r * rh, len, rh);
        // grain
        x.globalAlpha = 0.07;
        x.strokeStyle = '#5a3a20';
        for (let i = 0; i < 6; i++) {
          x.lineWidth = 0.6 + R() * 1.4;
          x.beginPath();
          const y0 = r * rh + R() * rh;
          x.moveTo(px, y0);
          x.bezierCurveTo(px + len * .3, y0 + (R() - .5) * 10, px + len * .6, y0 + (R() - .5) * 10, px + len, y0 + (R() - .5) * 6);
          x.stroke();
        }
        // a knot, sometimes
        if (R() < 0.18) {
          x.globalAlpha = 0.12; x.fillStyle = '#5a3a20';
          x.beginPath(); x.ellipse(px + R() * len, r * rh + rh * (.3 + R() * .4), 9, 4, 0, 0, 7); x.fill();
        }
        x.globalAlpha = 1;
        // end seam
        x.fillStyle = 'rgba(40,24,14,.55)';
        x.fillRect(px, r * rh, 2, rh);
        px += len;
      }
      x.fillStyle = 'rgba(40,24,14,.6)';
      x.fillRect(0, r * rh, w, 2.5);
      x.fillStyle = 'rgba(255,255,255,.08)';
      x.fillRect(0, r * rh + 2.5, w, 1.5);
    }
  }, { repeat: [3.2, 1.6], aniso: 8 });
}

/** Soft painted plaster with a faint hand-painted dot pattern. */
export function wallTexture() {
  const R = mulberry(5);
  return make(512, 512, (x, w, h) => {
    x.fillStyle = '#f4f4f2'; x.fillRect(0, 0, w, h);
    for (let i = 0; i < 2600; i++) {
      const v = 225 + R() * 30 | 0;
      x.fillStyle = `rgba(${v},${v},${v},.22)`;
      x.fillRect(R() * w, R() * h, 2 + R() * 6, 2 + R() * 6);
    }
    x.fillStyle = 'rgba(255,255,255,.35)';
    for (let yy = 0; yy < 8; yy++) for (let xx = 0; xx < 8; xx++) {
      x.beginPath(); x.arc(xx * 64 + 32 + (yy % 2) * 32, yy * 64 + 32, 3, 0, 7); x.fill();
    }
  }, { repeat: [5, 2] });
}

/** Wainscot: vertical beadboard. */
export function beadboardTexture() {
  return make(256, 128, (x, w, h) => {
    x.fillStyle = '#fbf8f2'; x.fillRect(0, 0, w, h);
    for (let i = 0; i < 8; i++) {
      x.fillStyle = 'rgba(80,60,40,.22)'; x.fillRect(i * 32, 0, 2, h);
      x.fillStyle = 'rgba(255,255,255,.5)'; x.fillRect(i * 32 + 2, 0, 2, h);
    }
  }, { repeat: [12, 1] });
}

/** A round braided rug, seen from above. */
export function rugTexture() {
  return make(512, 512, (x, w, h) => {
    const cx = w / 2, cy = h / 2;
    const rings = ['#6E5A74', '#8C7690', '#D9B98E', '#8C7690', '#B4889B', '#6E5A74', '#E8D3B0', '#8C7690', '#6E5A74'];
    for (let i = 0; i < rings.length; i++) {
      const r = (w / 2 - 4) * (1 - i / rings.length);
      x.fillStyle = rings[i];
      x.beginPath(); x.arc(cx, cy, r, 0, 7); x.fill();
      // braid stitches
      x.strokeStyle = 'rgba(0,0,0,.12)'; x.lineWidth = 2;
      for (let a = 0; a < 90; a++) {
        const t = a / 90 * Math.PI * 2;
        x.beginPath();
        x.moveTo(cx + Math.cos(t) * (r - 2), cy + Math.sin(t) * (r - 2));
        x.lineTo(cx + Math.cos(t + .05) * (r - 12), cy + Math.sin(t + .05) * (r - 12));
        x.stroke();
      }
    }
    // little fringe
    x.strokeStyle = '#E8D3B0'; x.lineWidth = 3;
    for (let a = 0; a < 140; a++) {
      const t = a / 140 * Math.PI * 2;
      x.beginPath(); x.moveTo(cx + Math.cos(t) * (w / 2 - 6), cy + Math.sin(t) * (h / 2 - 6));
      x.lineTo(cx + Math.cos(t) * (w / 2), cy + Math.sin(t) * (h / 2)); x.stroke();
    }
  });
}

/** Radial soft blob: contact shadows, glows, light pools. */
export function blobTexture(inner = 'rgba(0,0,0,1)', mid = 'rgba(0,0,0,.45)', size = 128) {
  return make(size, size, (x, w) => {
    const g = x.createRadialGradient(w / 2, w / 2, 0, w / 2, w / 2, w / 2);
    g.addColorStop(0, inner); g.addColorStop(.45, mid); g.addColorStop(1, 'rgba(0,0,0,0)');
    x.fillStyle = g; x.fillRect(0, 0, w, w);
  }, { srgb: false, aniso: 1 });
}

/** The window's light on the floor: four soft panes. */
export function sunPatchTexture() {
  return make(256, 256, (x, w, h) => {
    x.filter = 'blur(7px)';
    x.fillStyle = '#fff';
    const m = 26, gap = 10;
    const pw = (w - m * 2 - gap) / 2, ph = (h - m * 2 - gap) / 2;
    for (let i = 0; i < 2; i++) for (let j = 0; j < 2; j++) x.fillRect(m + i * (pw + gap), m + j * (ph + gap), pw, ph);
  }, { srgb: false, aniso: 1 });
}

/** A light shaft: bright at the window, fading into the room. */
export function shaftTexture() {
  return make(64, 256, (x, w, h) => {
    const g = x.createLinearGradient(0, 0, 0, h);
    g.addColorStop(0, 'rgba(255,255,255,.9)'); g.addColorStop(.6, 'rgba(255,255,255,.25)'); g.addColorStop(1, 'rgba(255,255,255,0)');
    x.fillStyle = g; x.fillRect(0, 0, w, h);
    const s = x.createLinearGradient(0, 0, w, 0);
    s.addColorStop(0, 'rgba(0,0,0,1)'); s.addColorStop(.2, 'rgba(0,0,0,0)'); s.addColorStop(.8, 'rgba(0,0,0,0)'); s.addColorStop(1, 'rgba(0,0,0,1)');
    x.globalCompositeOperation = 'destination-out'; x.fillStyle = s; x.fillRect(0, 0, w, h);
  }, { srgb: false, aniso: 1 });
}

/** A fluffy cumulus, painted with overlapping soft circles. */
export function cloudTexture(seed = 3) {
  const R = mulberry(seed);
  return make(256, 128, (x, w, h) => {
    for (let i = 0; i < 26; i++) {
      const cx = 40 + R() * (w - 80), cy = h * .62 - R() * h * .3 * Math.sin((cx / w) * Math.PI);
      const r = 18 + R() * 26;
      const g = x.createRadialGradient(cx, cy, 0, cx, cy, r);
      g.addColorStop(0, 'rgba(255,255,255,.95)'); g.addColorStop(.6, 'rgba(255,255,255,.55)'); g.addColorStop(1, 'rgba(255,255,255,0)');
      x.fillStyle = g; x.beginPath(); x.arc(cx, cy, r, 0, 7); x.fill();
    }
    // soft grey belly
    const b = x.createLinearGradient(0, h * .4, 0, h);
    b.addColorStop(0, 'rgba(0,0,0,0)'); b.addColorStop(1, 'rgba(120,130,150,.35)');
    x.globalCompositeOperation = 'source-atop'; x.fillStyle = b; x.fillRect(0, 0, w, h);
  }, { aniso: 1 });
}

/** Little hand-lettered sign. */
export function signTexture(text, bg = '#F4EFE6', fg = '#2B3342', w = 256, h = 64) {
  return make(w, h, (x) => {
    x.fillStyle = bg; x.fillRect(0, 0, w, h);
    x.fillStyle = fg;
    x.font = `800 ${h * .52}px ui-rounded, "Nunito", "Segoe UI", sans-serif`;
    x.textAlign = 'center'; x.textBaseline = 'middle';
    x.fillText(text, w / 2, h / 2 + 2);
  });
}

/** Crayon drawing on paper: Pipo's art, each one different. */
export function drawingTexture(kind) {
  const R = mulberry(kind * 7 + 1);
  return make(96, 120, (x, w, h) => {
    x.fillStyle = '#F2EADB'; x.fillRect(0, 0, w, h);
    x.lineCap = 'round'; x.lineJoin = 'round'; x.lineWidth = 3.2;
    const wob = (v) => v + (R() - .5) * 3;
    const C = ['#E0706A', '#6FA8E8', '#7FB77E', '#F3C35A', '#C99BFF', '#FF9E6B'];
    if (kind === 0) { // the sun, with a face
      x.strokeStyle = C[3]; x.beginPath(); x.arc(48, 52, 20, 0, 7); x.stroke();
      for (let i = 0; i < 10; i++) { const a = i / 10 * 7; x.beginPath(); x.moveTo(48 + Math.cos(a) * 26, 52 + Math.sin(a) * 26); x.lineTo(48 + Math.cos(a) * 36, 52 + Math.sin(a) * 36); x.stroke(); }
      x.fillStyle = '#2B3342'; x.fillRect(40, 46, 3, 4); x.fillRect(53, 46, 3, 4);
      x.beginPath(); x.arc(48, 56, 7, .3, 2.8); x.stroke();
    } else if (kind === 1) { // Nib
      x.fillStyle = '#FFB86B'; x.beginPath(); x.arc(48, 74, 24, Math.PI, 0); x.fill();
      x.fillStyle = '#2B3342'; x.fillRect(22, 72, 52, 6);
      x.fillStyle = '#141B27'; x.beginPath(); x.arc(52, 62, 8, 0, 7); x.fill();
      x.fillStyle = '#8FF5E2'; x.beginPath(); x.arc(53, 62, 4, 0, 7); x.fill();
      x.strokeStyle = C[0]; x.beginPath(); x.moveTo(30, 30); x.lineTo(38, 22); x.lineTo(46, 30); x.stroke();
    } else if (kind === 2) { // "you", a circle (it's accurate)
      x.strokeStyle = C[1]; x.beginPath(); x.ellipse(48, 58, 26, 30, .1, 0, 7); x.stroke();
      x.fillStyle = '#2B3342'; x.fillRect(38, 50, 3, 3); x.fillRect(56, 50, 3, 3);
      x.beginPath(); x.moveTo(40, 68); x.lineTo(56, 66); x.stroke();
    } else if (kind === 3) { // house + tree
      x.strokeStyle = C[0]; x.strokeRect(20, 60, 40, 34); x.beginPath(); x.moveTo(16, 62); x.lineTo(40, 38); x.lineTo(64, 62); x.stroke();
      x.strokeStyle = C[2]; x.beginPath(); x.arc(76, 58, 12, 0, 7); x.stroke(); x.beginPath(); x.moveTo(76, 70); x.lineTo(76, 94); x.stroke();
    } else if (kind === 4) { // a screw, lovingly
      x.strokeStyle = '#8E99A8'; x.beginPath(); x.moveTo(48, 22); x.lineTo(48, 92); x.stroke();
      for (let i = 0; i < 7; i++) { x.beginPath(); x.moveTo(wob(38), 34 + i * 8); x.lineTo(wob(58), 38 + i * 8); x.stroke(); }
      x.fillStyle = '#8E99A8'; x.fillRect(32, 18, 32, 8);
      x.fillStyle = C[4]; x.font = '700 14px sans-serif'; x.fillText('♥', 70, 30);
    } else { // rain and a little umbrella
      x.strokeStyle = C[1];
      for (let i = 0; i < 12; i++) { const px = 12 + R() * 72, py = 14 + R() * 40; x.beginPath(); x.moveTo(px, py); x.lineTo(px - 3, py + 8); x.stroke(); }
      x.strokeStyle = C[0]; x.beginPath(); x.arc(48, 76, 22, Math.PI, 0); x.closePath(); x.stroke();
      x.beginPath(); x.moveTo(48, 76); x.lineTo(48, 100); x.arc(44, 100, 4, 0, Math.PI); x.stroke();
    }
  }, { aniso: 1 });
}

/** Nib doesn't do many words. It thinks in little pictures. */
export function thoughtTexture(kind) {
  return make(128, 128, (x, w) => {
    x.fillStyle = 'rgba(244,239,230,.97)';
    x.beginPath(); x.arc(64, 54, 44, 0, 7); x.fill();
    x.beginPath(); x.arc(40, 108, 9, 0, 7); x.fill();
    x.beginPath(); x.arc(26, 122, 5, 0, 7); x.fill();
    x.lineCap = 'round'; x.lineJoin = 'round';
    const ink = '#3B4658';
    if (kind === 'ball') {
      x.fillStyle = '#E98A7A'; x.beginPath(); x.arc(64, 54, 22, 0, 7); x.fill();
      x.strokeStyle = '#fff'; x.lineWidth = 4; x.beginPath(); x.moveTo(42, 54); x.lineTo(86, 54); x.stroke();
    } else if (kind === 'pipo') {
      x.fillStyle = '#E8ECF1'; x.beginPath(); x.roundRect(36, 34, 56, 40, 14); x.fill();
      x.fillStyle = '#141B27'; x.beginPath(); x.roundRect(43, 41, 42, 26, 9); x.fill();
      x.fillStyle = '#8FF5E2'; x.beginPath(); x.roundRect(52, 47, 7, 11, 3.5); x.fill(); x.beginPath(); x.roundRect(69, 47, 7, 11, 3.5); x.fill();
      x.strokeStyle = '#3B4658'; x.lineWidth = 3; x.beginPath(); x.moveTo(64, 34); x.lineTo(64, 24); x.stroke();
      x.fillStyle = '#8FF5E2'; x.beginPath(); x.arc(64, 21, 5, 0, 7); x.fill();
    } else if (kind === 'heart') {
      x.fillStyle = '#FF7A90'; x.beginPath(); x.moveTo(64, 78);
      x.bezierCurveTo(28, 56, 40, 24, 64, 42); x.bezierCurveTo(88, 24, 100, 56, 64, 78); x.fill();
    } else if (kind === 'question') {
      x.strokeStyle = ink; x.lineWidth = 8; x.beginPath(); x.arc(64, 44, 13, Math.PI * 1.05, Math.PI * 2.35); x.lineTo(64, 64); x.stroke();
      x.fillStyle = ink; x.beginPath(); x.arc(64, 78, 5, 0, 7); x.fill();
    } else if (kind === 'exclaim') {
      x.strokeStyle = '#E0605A'; x.lineWidth = 10; x.beginPath(); x.moveTo(64, 28); x.lineTo(64, 62); x.stroke();
      x.fillStyle = '#E0605A'; x.beginPath(); x.arc(64, 78, 6, 0, 7); x.fill();
    } else if (kind === 'bird') {
      x.strokeStyle = ink; x.lineWidth = 6;
      x.beginPath(); x.moveTo(36, 50); x.quadraticCurveTo(50, 34, 64, 52); x.quadraticCurveTo(78, 34, 92, 50); x.stroke();
    } else if (kind === 'zzz') {
      x.strokeStyle = '#7F8CE0'; x.lineWidth = 5;
      const z = (cx, cy, s) => { x.beginPath(); x.moveTo(cx - s, cy - s); x.lineTo(cx + s, cy - s); x.lineTo(cx - s, cy + s); x.lineTo(cx + s, cy + s); x.stroke(); };
      z(50, 64, 10); z(74, 44, 7);
    } else if (kind === 'gear') {
      x.fillStyle = '#9AA7B8';
      for (let i = 0; i < 8; i++) { x.save(); x.translate(64, 54); x.rotate(i / 8 * Math.PI * 2); x.fillRect(-5, -26, 10, 12); x.restore(); }
      x.beginPath(); x.arc(64, 54, 18, 0, 7); x.fill();
      x.fillStyle = 'rgba(244,239,230,1)'; x.beginPath(); x.arc(64, 54, 7, 0, 7); x.fill();
    } else if (kind === 'drop') {
      x.fillStyle = '#7FB6FF'; x.beginPath(); x.moveTo(64, 26); x.quadraticCurveTo(88, 60, 64, 76); x.quadraticCurveTo(40, 60, 64, 26); x.fill();
    } else if (kind === 'mirror') {
      x.fillStyle = '#B98A55'; x.beginPath(); x.roundRect(48, 22, 32, 62, 6); x.fill();
      x.fillStyle = '#CFE3EA'; x.beginPath(); x.roundRect(53, 27, 22, 52, 4); x.fill();
      x.strokeStyle = '#E0605A'; x.lineWidth = 6; x.beginPath(); x.moveTo(90, 30); x.lineTo(90, 56); x.stroke();
      x.fillStyle = '#E0605A'; x.beginPath(); x.arc(90, 68, 4, 0, 7); x.fill();
    } else if (kind === 'sock') {
      x.fillStyle = '#E0706A'; x.beginPath(); x.moveTo(52, 26); x.lineTo(70, 26); x.lineTo(70, 58); x.quadraticCurveTo(84, 62, 82, 74); x.lineTo(56, 74); x.quadraticCurveTo(50, 70, 52, 60); x.fill();
      x.fillStyle = '#fff'; x.fillRect(52, 26, 18, 7);
    }
  }, { aniso: 1 });
}
