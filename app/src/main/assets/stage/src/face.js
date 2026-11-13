import * as THREE from 'three';
import { EXPR } from './pipo.js';

/*
 * Pipo's face, rendered as a lit screen (same identity as the app: two mint pill eyes and a
 * small smile on a dark-blue display). Everything is drawn crisp on one canvas:
 *  - the screen itself is a soft gradient with a faint glow, never flat black
 *  - eyes are solid shapes with a soft inner gradient and one controlled glow
 *  - lids are clips of the eye shape, never overlay rectangles (those showed as translucent bands)
 */

export const FW = 512, FH = 344;
const EYE_DX = 92, EYE_Y = FH * 0.45, EYE_W = 64, EYE_H = 86;
const MOUTH_Y = FH * 0.75;

const DEFAULT = {
  open: 0, scale: 1, lidTop: 0, lidAngle: 0, arc: 0, closed: 0, round: 0, asym: 0, blush: 0,
  sparkle: 0.4, squint: 0, curve: 0.45, skew: 0, wave: 0, wink: 0, width: 0.9,
  lookX: 0, lookY: 0, blink: 0, talk: 0,
};

function rrect(x, cx, cy, w, h, r) {
  const q = Math.max(0, Math.min(r, w / 2, h / 2));
  x.beginPath();
  x.roundRect(cx - w / 2, cy - h / 2, w, h, q);
}
function shade(hex, k) {
  const c = new THREE.Color(hex);
  return k >= 0 ? c.lerp(new THREE.Color(0xffffff), k) : c.multiplyScalar(1 + k);
}

export class ScreenFace {
  constructor() {
    this.cur = { ...DEFAULT };
    this.tgt = { ...DEFAULT };
    this.eyeColor = new THREE.Color(0x8FF5E2);
    this.blinkT = 0; this.nextBlink = 2 + Math.random() * 2.5; this.doubleBlink = false;
    this.gazeT = 1.5; this.gazeX = 0; this.gazeY = 0;
    this.override = null;       // { x, y } gaze held by app state (thinking looks up, listening looks at you)
    this.canvas = document.createElement('canvas');
    this.canvas.width = FW; this.canvas.height = FH;
    this.ctx = this.canvas.getContext('2d');
    this.tex = new THREE.CanvasTexture(this.canvas);
    this.tex.colorSpace = THREE.SRGBColorSpace;
    this.tex.anisotropy = 4;
    this.bg = document.createElement('canvas');
    this.bg.width = FW; this.bg.height = FH;
    this._paintScreen();
  }

  setExpr(name, moodGlow) {
    const e = EXPR[name] || EXPR.CONTENT;
    Object.assign(this.tgt, DEFAULT, e);
    // keep him friendly: soften the harsh presets instead of slanting the eyes hard
    this.tgt.lidAngle *= 0.55;
    this.tgt.scale = Math.min(this.tgt.scale, 1.1);
    if (name === 'CONTENT') this.tgt.curve = 0.5;
    if (moodGlow !== undefined) {
      const c = new THREE.Color(moodGlow);
      if (!c.equals(this.eyeColor)) { this.eyeColor.copy(c); this._paintScreen(); }
    }
  }

  /** Held gaze from app state, or null to let him glance around naturally. */
  hold(x, y) { this.override = x === null ? null : { x, y }; }
  lookAt(x, y) { this.tgt.lookX = x; this.tgt.lookY = y; }

  update(dt, sleepy, talking) {
    // blinks: every 2-5 s, sometimes a double; slower when sleepy
    const dur = sleepy ? 0.34 : 0.16;
    this.nextBlink -= dt;
    if (this.nextBlink <= 0) {
      this.blinkT = dur;
      this.nextBlink = (sleepy ? 1.4 : 2.2) + Math.random() * (sleepy ? 1.6 : 3);
      this.doubleBlink = Math.random() < 0.18;
    }
    let blink = 0;
    if (this.blinkT > 0) {
      this.blinkT -= dt;
      blink = Math.sin(Math.PI * Math.min(1, Math.max(0, 1 - this.blinkT / dur)));
      if (this.blinkT <= 0 && this.doubleBlink) { this.doubleBlink = false; this.blinkT = dur; }
    }
    // gaze: small glances that mostly come back to you
    this.gazeT -= dt;
    if (this.gazeT <= 0) {
      this.gazeT = 1.6 + Math.random() * 2.8;
      const back = Math.random() < 0.45;
      this.gazeX = back ? 0 : (Math.random() * 2 - 1) * (sleepy ? 0.15 : 0.32);
      this.gazeY = back ? 0 : (Math.random() * 2 - 1) * 0.18;
    }
    const c = this.cur, t = this.tgt;
    const kE = 1 - Math.exp(-12 * dt), kM = 1 - Math.exp(-6 * dt), kG = 1 - Math.exp(-9 * dt);
    for (const k of ['open', 'scale', 'lidTop', 'lidAngle', 'arc', 'closed', 'round', 'asym', 'sparkle', 'squint', 'wink']) c[k] += (t[k] - c[k]) * kE;
    for (const k of ['blush', 'curve', 'skew', 'wave', 'width']) c[k] += (t[k] - c[k]) * kM;
    const gx = this.override ? this.override.x : this.gazeX + t.lookX * 0.4;
    const gy = this.override ? this.override.y : this.gazeY + t.lookY * 0.4;
    c.lookX += (Math.max(-1, Math.min(1, gx)) - c.lookX) * kG;
    c.lookY += (Math.max(-1, Math.min(1, gy)) - c.lookY) * kG;
    c.blink = blink;
    c.talk = talking ? Math.min(1, c.talk + dt * 8) : Math.max(0, c.talk - dt * 6);
    this.draw();
    this.tex.needsUpdate = true;
  }

  /** The screen: gradient, a faint mint glow behind the features, scanlines, a top reflection, a bevel. */
  _paintScreen() {
    const x = this.bg.getContext('2d');
    x.clearRect(0, 0, FW, FH);
    x.save();
    rrect(x, FW / 2, FH / 2, FW, FH, 70); x.clip();
    const g = x.createLinearGradient(0, 0, 0, FH);
    g.addColorStop(0, '#2A4166'); g.addColorStop(0.55, '#1C2C47'); g.addColorStop(1, '#141F33');
    x.fillStyle = g; x.fillRect(0, 0, FW, FH);
    const glow = x.createRadialGradient(FW / 2, FH * 0.5, 10, FW / 2, FH * 0.5, FW * 0.55);
    const ec = this.eyeColor;
    glow.addColorStop(0, `rgba(${ec.r * 255 | 0},${ec.g * 255 | 0},${ec.b * 255 | 0},0.16)`);
    glow.addColorStop(1, 'rgba(0,0,0,0)');
    x.fillStyle = glow; x.fillRect(0, 0, FW, FH);
    x.fillStyle = 'rgba(255,255,255,0.025)';
    for (let y = 0; y < FH; y += 4) x.fillRect(0, y, FW, 1);
    const top = x.createLinearGradient(0, 0, 0, FH * 0.38);
    top.addColorStop(0, 'rgba(255,255,255,0.13)'); top.addColorStop(1, 'rgba(255,255,255,0)');
    x.fillStyle = top; x.fillRect(0, 0, FW, FH * 0.38);
    x.restore();
    x.lineWidth = 5; x.strokeStyle = 'rgba(255,255,255,0.10)';
    rrect(x, FW / 2, FH / 2, FW - 5, FH - 5, 68); x.stroke();
  }

  draw() {
    const x = this.ctx, f = this.cur;
    x.clearRect(0, 0, FW, FH);
    x.drawImage(this.bg, 0, 0);
    const col = '#' + this.eyeColor.getHexString();
    const light = '#' + shade(this.eyeColor, 0.55).getHexString();
    const deep = '#' + shade(this.eyeColor, -0.22).getHexString();
    const gx = f.lookX * 16, gy = f.lookY * 11;

    for (const side of [-1, 1]) {
      const wob = side < 0 && f.asym > 0 ? 1 + f.asym * 0.25 : side > 0 && f.asym < 0 ? 1 - f.asym * 0.25 : 1;
      const cx = FW / 2 + side * EYE_DX + gx, cy = EYE_Y + gy;
      let w = EYE_W * f.scale * wob, h = EYE_H * f.scale * wob;
      if (f.round > 0.3) { w = h = Math.max(w, h * 0.86) * (1 + (f.round - 0.3) * 0.15); }
      const winkThis = side < 0 ? f.wink > 0 : f.wink < 0;
      const closed = Math.max(f.closed, winkThis ? 1 : 0);

      x.save();
      x.shadowColor = col; x.shadowBlur = 16;
      x.lineCap = 'round';
      if (f.arc > 0.05 && closed < 0.5) {
        // happy: soft ^ arcs
        x.globalAlpha = Math.min(1, f.arc + 0.1);
        x.strokeStyle = col; x.lineWidth = 15;
        x.beginPath(); x.moveTo(cx - w * 0.55, cy + h * 0.12); x.quadraticCurveTo(cx, cy - h * 0.5, cx + w * 0.55, cy + h * 0.12); x.stroke();
      } else if (closed > 0.5 || f.blink > 0.9) {
        // closed / mid-blink: a gentle line
        x.strokeStyle = col; x.lineWidth = 12;
        x.beginPath(); x.moveTo(cx - w * 0.5, cy); x.quadraticCurveTo(cx, cy + (closed > 0.5 ? 14 : 4), cx + w * 0.5, cy); x.stroke();
      } else {
        // open eye: crisp pill, lids and squint as clips
        const hh = h * (1 - f.blink * 0.9);
        const top = cy - hh / 2, bot = cy + hh / 2;
        const lid = f.lidTop * hh * 0.55, tilt = -side * f.lidAngle * 0.32;
        // with no lid/squint the clip stands well clear, so the glow isn't cut into a hard edge
        const lidded = f.lidTop > 0.03 || Math.abs(f.lidAngle) > 0.03;
        const cutTop = lidded ? top + lid : top - 30, cutBot = f.squint > 0.03 ? bot - f.squint * hh * 0.45 : bot + 30;
        x.beginPath();
        x.moveTo(cx - w, cutTop + (lidded ? tilt * -w : 0)); x.lineTo(cx + w, cutTop + (lidded ? tilt * w : 0));
        x.lineTo(cx + w, cutBot); x.lineTo(cx - w, cutBot); x.closePath();
        x.clip();
        const grad = x.createLinearGradient(0, top, 0, bot);
        grad.addColorStop(0, light); grad.addColorStop(0.45, col); grad.addColorStop(1, deep);
        x.fillStyle = grad;
        rrect(x, cx, cy, w, hh, Math.min(w, hh) / 2); x.fill();
        x.shadowBlur = 0;
        // highlights: one clear catchlight, a second small one when he's bright-eyed; they drift a touch against the gaze for depth
        x.fillStyle = 'rgba(255,255,255,0.92)';
        x.beginPath(); x.ellipse(cx - w * 0.17 - gx * 0.15, top + hh * 0.27 - gy * 0.1, w * 0.15, Math.max(2, hh * 0.1), -0.35, 0, Math.PI * 2); x.fill();
        if (f.sparkle > 0.3) {
          x.globalAlpha = 0.65 * Math.min(1, (f.sparkle - 0.3) * 3);
          x.beginPath(); x.arc(cx + w * 0.18 - gx * 0.1, cy + hh * 0.2, w * 0.07, 0, Math.PI * 2); x.fill();
        }
      }
      x.restore();
    }

    // mouth: small, soft, the same light as the eyes
    const mw = 30 * (f.width || 0.9), mx = FW / 2 + gx * 0.5, my = MOUTH_Y + gy * 0.4;
    const open = f.talk > 0.05 ? Math.max(f.open, (0.35 + 0.65 * Math.abs(Math.sin(performance.now() * 0.013))) * f.talk) : f.open;
    x.save();
    x.shadowColor = col; x.shadowBlur = 10;
    x.strokeStyle = col; x.fillStyle = col; x.lineCap = 'round'; x.globalAlpha = 0.95;
    if (open > 0.12) {
      const hgt = 8 + open * 22;
      x.beginPath();
      x.moveTo(mx - mw * 0.8, my - 2);
      x.quadraticCurveTo(mx, my - 6, mx + mw * 0.8, my - 2);
      x.quadraticCurveTo(mx, my + hgt + f.curve * 6, mx - mw * 0.8, my - 2);
      x.fill();
    } else if (f.wave > 0.3) {
      x.lineWidth = 8;
      x.beginPath(); x.moveTo(mx - mw, my);
      x.quadraticCurveTo(mx - mw * 0.5, my - 9 * f.wave, mx, my); x.quadraticCurveTo(mx + mw * 0.5, my + 9 * f.wave, mx + mw, my); x.stroke();
    } else {
      x.lineWidth = 8;
      x.beginPath(); x.moveTo(mx - mw, my - f.curve * 4 + f.skew * 5); x.quadraticCurveTo(mx, my + f.curve * 18, mx + mw, my - f.curve * 4 - f.skew * 5); x.stroke();
    }
    x.restore();

    // blush: soft round glows, not hard ellipses
    if (f.blush > 0.02) {
      for (const side of [-1, 1]) {
        const bx = FW / 2 + side * 148 + gx, by = FH * 0.66 + gy;
        const g = x.createRadialGradient(bx, by, 2, bx, by, 38);
        g.addColorStop(0, `rgba(255,143,163,${0.6 * f.blush})`); g.addColorStop(1, 'rgba(255,143,163,0)');
        x.fillStyle = g; x.fillRect(bx - 40, by - 40, 80, 80);
      }
    }
  }
}
