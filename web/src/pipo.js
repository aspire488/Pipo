import * as THREE from 'three';
import { RoundedBoxGeometry } from '../vendor/RoundedBoxGeometry.js';

/* ============ shared helpers ============ */

export const C = {
  shell: 0xF4F6F9, shellShade: 0xC9D2DE, belly: 0xE3E8EF,
  screen: 0x141B27, joint: 0x3B4658, eye: 0x8FF5E2, blush: 0xFF8FA3, arm: 0xD9E0E9,
  nibShell: 0xFFB86B, nibShade: 0xD9874A, nibBand: 0x3B4658,
  amber: 0xFFC27A, lilac: 0xC99BFF, coral: 0xE0706A, paper: 0xF4EFE6,
};

export const MOOD_GLOW = {
  HAPPY: 0xFFC27A, EXCITED: 0xFFD36E, SLEEPY: 0x9FA8FF, BORED: 0x9AA7B8,
  GRUMPY: 0xFF7A6B, LONELY: 0x7FB6FF, NERVOUS: 0xFFE08A, EMBARRASSED: 0xFF8FA3,
  MISCHIEVOUS: 0xC99BFF, CURIOUS: 0x8FF5E2, RELAXED: 0x8FF5E2, WORRIED: 0xB8C8E8,
  THOUGHTFUL: 0xA8C8FF, PLAYFUL: 0xFFB86B, PROUD: 0xFFC27A,
};

const mat = (color, opts = {}) =>
  new THREE.MeshStandardMaterial({ color, roughness: opts.rough ?? 0.62, metalness: opts.metal ?? 0.04, ...opts.extra });

function radialTexture(inner = 'rgba(255,255,255,1)', outer = 'rgba(255,255,255,0)', size = 128) {
  const c = document.createElement('canvas'); c.width = c.height = size;
  const x = c.getContext('2d');
  const g = x.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2);
  g.addColorStop(0, inner); g.addColorStop(0.35, inner.replace('1)', '.55)')); g.addColorStop(1, outer);
  x.fillStyle = g; x.fillRect(0, 0, size, size);
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace;
  return t;
}
const GLOW_TEX = radialTexture();
const SOFT_TEX = radialTexture('rgba(255,255,255,.9)', 'rgba(255,255,255,0)');

export function glowSprite(color, scale, opacity = 0.85) {
  const m = new THREE.SpriteMaterial({
    map: GLOW_TEX, color, transparent: true, opacity,
    blending: THREE.AdditiveBlending, depthWrite: false,
  });
  const s = new THREE.Sprite(m);
  s.scale.setScalar(scale);
  return s;
}

function box(w, h, d, r, color, opts) {
  const rr = Math.min(r, w / 2 - 0.01, h / 2 - 0.01, d / 2 - 0.01);
  const g = rr > 0.02 ? new RoundedBoxGeometry(w, h, d, 3, rr) : new THREE.BoxGeometry(w, h, d);
  const m = new THREE.Mesh(g, mat(color, opts));
  m.castShadow = true; m.receiveShadow = true;
  return m;
}

/* ============ face (canvas texture) ============ */

const FW = 416, FH = 296;               // canvas px for the 52 x 37 face screen
const UX = FW / 52, UY = FH / 37;
const px = x => (x + 26) * UX;           // rig x (-26..26) -> canvas px
const py = y => (83 - y) * UY;           // rig y (46..83) -> canvas px

function rr(x, cx, cy, w, h, r) {
  const q = Math.min(r, w / 2, h / 2);
  x.beginPath();
  if (x.roundRect) x.roundRect(cx, cy, w, h, q);
  else {
    x.moveTo(cx - w / 2 + q, cy - h / 2);
    x.arcTo(cx + w / 2, cy - h / 2, cx + w / 2, cy + h / 2, q);
    x.arcTo(cx + w / 2, cy + h / 2, cx - w / 2, cy + h / 2, q);
    x.arcTo(cx - w / 2, cy + h / 2, cx - w / 2, cy - h / 2, q);
    x.arcTo(cx - w / 2, cy - h / 2, cx + w / 2, cy - h / 2, q);
    x.closePath();
  }
  x.fill();
}

export const EXPR = {
  CONTENT:    { curve: .45 },
  HAPPY:      { arc: 1, curve: 1, open: .35, width: 1.1, blush: .15 },
  EXCITED:    { scale: 1.2, sparkle: 1, round: .3, curve: .9, open: .6, width: 1.15, pupil: 1.35 },
  CURIOUS:    { asym: .35, scale: 1.05, curve: 0, open: .2, width: .55, pupil: 1.3 },
  SLEEPY:     { open: .4, lidTop: .45, scale: .95, curve: 0, width: .6, pupil: .9 },
  BORED:      { open: .9, lidTop: .5, curve: -.05, skew: .3 },
  SURPRISED:  { round: 1, scale: 1.2, open: .8, width: .6, pupil: .55 },
  ANNOYED:    { lidAngle: .9, lidTop: .25, curve: -.5, width: .8, pupil: .8 },
  EMBARRASSED:{ arc: .75, blush: 1, wave: .6, curve: .2, width: .7 },
  SAD:        { lidAngle: -.8, lidTop: .15, scale: .95, curve: -.7, width: .7 },
  SUSPICIOUS: { open: .5, lidTop: .3, asym: -.3, skew: .6, curve: 0, pupil: .85 },
  NERVOUS:    { scale: .85, wave: 1, curve: 0, width: .9, pupil: .7 },
  PROUD:      { squint: .5, lidTop: .2, curve: .85, skew: .4 },
  MISCHIEF:   { lidTop: .35, lidAngle: .35, squint: .3, curve: .7, skew: .8 },
  CLOSED:     { closed: 1, curve: .1, width: .5 },
  FOCUSED:    { open: .9, lidTop: .3, curve: 0, width: .5, skew: .2, pupil: .9 },
  WINK:       { wink: 1, curve: .8, skew: .6, width: .95, blush: .2 },
  LAUGH:      { arc: 1, squint: .2, curve: 1, open: .75, width: 1.15, blush: .25 },
  WORRIED:    { lidAngle: -.6, scale: .92, wave: .5, curve: -.2, width: .75, pupil: .8 },
  LOVE:       { arc: 1, blush: 1, curve: 1, width: .9 },
};

const FACE_DEFAULT = {
  open: .12, scale: 1, lidTop: 0, lidAngle: 0, arc: 0, closed: 0, round: 0,
  asym: 0, blush: 0, sparkle: .35, squint: 0, curve: .3, skew: 0, wave: 0,
  wink: 0, pupil: 1, lookX: 0, lookY: 0, blink: 0, talk: 0, eyeColor: '#8FF5E2',
};

export class Face {
  constructor() {
    this.cur = { ...FACE_DEFAULT };
    this.tgt = { ...FACE_DEFAULT };
    this.blinkT = 0; this.nextBlink = 1.8 + Math.random() * 3.5; this.blinkQueue = 0;
    this.sacT = 0; this.sacX = 0; this.sacY = 0;
    this.canvas = document.createElement('canvas');
    this.canvas.width = FW; this.canvas.height = FH;
    this.ctx = this.canvas.getContext('2d');
    this.tex = new THREE.CanvasTexture(this.canvas);
    this.tex.colorSpace = THREE.SRGBColorSpace;
    this.dirty = true;
  }

  setExpr(name, moodGlow) {
    const e = EXPR[name] || EXPR.CONTENT;
    const base = { ...FACE_DEFAULT };
    Object.assign(this.tgt, base, e);
    if (moodGlow) this.tgt.eyeColor = '#' + new THREE.Color(moodGlow).getHexString();
  }

  lookAt(lx, ly) { this.tgt.lookX = lx; this.tgt.lookY = ly; }

  update(dt, sleepy, talking) {
    // blink
    this.nextBlink -= dt;
    if (this.nextBlink <= 0) {
      this.blinkT = sleepy ? 0.35 : 0.15;
      this.nextBlink = (sleepy ? 1.2 : 1.8) + Math.random() * (sleepy ? 1.5 : 3.5);
      if (Math.random() < 0.15) this.blinkQueue = 0.12;
    }
    let blink = 0;
    if (this.blinkT > 0) {
      this.blinkT -= dt;
      blink = 1 - Math.sin(Math.PI * Math.min(1, Math.max(0, (sleepy ? 0.35 : 0.15) - this.blinkT) / (sleepy ? 0.35 : 0.15)));
      if (this.blinkT <= 0 && this.blinkQueue > 0) { this.blinkT = 0.15; this.blinkQueue = 0; }
    }
    // autonomous saccades
    this.sacT -= dt;
    if (this.sacT <= 0 && !talking) {
      this.sacT = 1.2 + Math.random() * 2.5;
      const r = sleepy ? 0.2 : 0.55;
      this.sacX = (Math.random() * 2 - 1) * r;
      this.sacY = (Math.random() * 2 - 1) * r * 0.6;
    }

    const kE = 1 - Math.exp(-14 * dt);
    const kM = 1 - Math.exp(-6.5 * dt);
    const c = this.cur, t = this.tgt;
    const lerpNum = (key, k) => { c[key] = t[key] + (c[key] - t[key]) * k; };
    for (const key of ['open', 'scale', 'lidTop', 'lidAngle', 'arc', 'closed', 'round', 'asym',
      'sparkle', 'squint', 'pupil', 'wink']) lerpNum(key, kE);
    for (const key of ['blush', 'curve', 'skew', 'wave']) lerpNum(key, kM);
    c.blink = blink;
    if (!this.lockLook) {
      c.lookX += (this.sacX - c.lookX) * kE;
      c.lookY += (this.sacY - c.lookY) * kE;
    } else {
      c.lookX += (t.lookX - c.lookX) * kE;
      c.lookY += (t.lookY - c.lookY) * kE;
    }
    c.talk = talking ? Math.min(1, c.talk + dt * 9) : Math.max(0, c.talk - dt * 6);
    c.eyeColor = t.eyeColor;

    this.draw();
    this.tex.needsUpdate = true;
  }

  draw() {
    const x = this.ctx, f = this.cur;
    x.clearRect(0, 0, FW, FH);

    // screen
    x.fillStyle = '#141B27';
    rr(x, FW / 2, FH / 2, FW - 6, FH - 6, 112);
    // glass sheen
    x.globalAlpha = 0.07; x.fillStyle = '#FFFFFF';
    x.save(); x.translate(FW / 2, FH / 2); x.rotate(-0.38);
    x.fillRect(-FW * 0.7, -FH * 0.75, FW * 0.62, FH * 1.5); x.restore();
    x.globalAlpha = 1;

    const eyeCol = f.eyeColor;
    const s = f.scale;
    for (const side of [-1, 1]) {
      const wob = side < 0 && f.asym > 0 ? 1 + f.asym * 0.35 : (side > 0 && f.asym < 0 ? 1 - f.asym * 0.35 : 1);
      const cx = px(side * 12) + f.lookX * 4.5 * UX;
      const cy = py(66) + f.lookY * 3 * UY;
      let hw = 5.2 * UX * s * wob;
      let hh = 7 * UY * s * wob;
      if (f.round > 0.3) hw *= 1 + (f.round - 0.3) * 0.5;
      hh *= (1 - f.blink * 0.88);
      hh = Math.max(hh, 7 * UY * 0.12);

      const closedEye = (side < 0 ? f.wink > 0 : f.wink < 0) ? 1 : 0;
      const isClosed = Math.max(f.closed, closedEye);

      // halo
      x.globalAlpha = 0.16 * (1 - isClosed);
      x.fillStyle = eyeCol;
      rr(x, cx, cy, hw * 3.2, hh * 2.8, hw * 1.6);

      if (f.arc > 0.05 && isClosed < 0.5) {
        // happy ^ arc
        x.globalAlpha = f.arc * 0.95;
        x.strokeStyle = eyeCol; x.lineWidth = 0.75 * 5.2 * UX * s; x.lineCap = 'round';
        x.beginPath();
        x.moveTo(cx - hw * 1.1, cy + hh * 0.35);
        x.quadraticCurveTo(cx, cy - hh * 1.1, cx + hw * 1.1, cy + hh * 0.35);
        x.stroke();
      } else if (isClosed > 0.05) {
        // closed ∪
        x.globalAlpha = 0.9 * isClosed + 0.9 * (1 - isClosed) * f.blink;
        x.strokeStyle = eyeCol; x.lineWidth = 0.6 * 5.2 * UX * s; x.lineCap = 'round';
        x.beginPath();
        x.moveTo(cx - hw * 1.1, cy - hh * 0.1);
        x.quadraticCurveTo(cx, cy + (0.9 * hh) + 4, cx + hw * 1.1, cy - hh * 0.1);
        x.stroke();
      } else {
        // pill eye
        x.globalAlpha = 1;
        x.fillStyle = eyeCol;
        rr(x, cx, cy, hw * 2, hh * 2, Math.min(hw, hh));
        // pupil
        const pr = hw * 0.5 * f.pupil;
        const ox = f.lookX * hw * 0.32, oy = f.lookY * hh * 0.3;
        if (f.round > 0.3) {
          x.globalAlpha = 1; x.fillStyle = '#141B27';
          x.beginPath(); x.arc(cx + ox, cy + oy, pr * 0.9, 0, 7); x.fill();
          x.fillStyle = eyeCol;
          x.beginPath(); x.arc(cx + ox, cy + oy, pr * 0.5, 0, 7); x.fill();
        } else {
          x.globalAlpha = 0.5; x.fillStyle = '#FFFFFF';
          x.beginPath(); x.arc(cx + ox, cy + oy, pr * 0.75, 0, 7); x.fill();
        }
        // highlights
        x.globalAlpha = (0.5 + 0.4 * f.sparkle);
        x.fillStyle = '#FFFFFF';
        x.beginPath(); x.arc(cx - hw * 0.35 + ox * .5, cy - hh * 0.45 + oy * .5, hw * (0.27 + 0.15 * f.sparkle), 0, 7); x.fill();
        if (f.sparkle > 0.3) {
          x.globalAlpha = 0.8; x.beginPath();
          x.arc(cx + hw * 0.35, cy + hh * 0.35, hw * 0.14, 0, 7); x.fill();
        }
        // lids
        if (f.lidTop > 0.02 || f.lidAngle !== 0) {
          x.globalAlpha = Math.min(1, f.lidTop + Math.abs(f.lidAngle) * 0.5);
          x.fillStyle = '#141B27';
          const lh = hh * 2 * Math.min(1, f.lidTop + 0.001);
          x.save();
          x.translate(cx, cy - hh);
          x.rotate(side * -f.lidAngle * 0.35);
          x.fillRect(-hw - 2, -hh, hw * 2 + 4, lh + Math.abs(f.lidAngle) * hh * 1.2);
          x.restore();
        }
        if (f.squint > 0.02) {
          x.globalAlpha = 1; x.fillStyle = '#141B27';
          x.fillRect(cx - hw - 2, cy + hh - f.squint * 1.2 * hh, hw * 2 + 4, hh * 2);
        }
      }
      x.globalAlpha = 1;
    }

    // mouth
    const mx = px(0), my = py(53.5), mw = 6.5 * UX * (f.width || .9);
    x.strokeStyle = eyeCol; x.fillStyle = eyeCol; x.globalAlpha = 0.92;
    const open = f.talk > 0.05 ? Math.max(f.open, Math.abs(Math.sin(performance.now() * 0.012)) * f.talk * 0.9) : f.open;
    if (f.wave > 0.3 && open < 0.2) {
      x.lineWidth = 9; x.lineCap = 'round';
      x.beginPath();
      x.moveTo(mx - mw, my);
      x.quadraticCurveTo(mx - mw * 0.4, my - 12 * f.wave, mx, my);
      x.quadraticCurveTo(mx + mw * 0.4, my + 12 * f.wave, mx + mw, my);
      x.stroke();
    } else if (open < 0.10) {
      x.lineWidth = 9.5; x.lineCap = 'round';
      x.beginPath();
      x.moveTo(mx - mw, my + f.skew * 8);
      x.quadraticCurveTo(mx, my - f.curve * 22, mx + mw, my - f.skew * 8);
      x.stroke();
    } else {
      x.beginPath();
      const top = my - 4 - open * 7;
      x.moveTo(mx - mw, top);
      x.quadraticCurveTo(mx, top - open * 14, mx + mw, top);
      x.quadraticCurveTo(mx, top + open * 40 + f.curve * 10, mx - mw, top);
      x.closePath(); x.fill();
      x.globalAlpha = 0.18; x.fillStyle = '#FFFFFF';
      x.beginPath(); x.arc(mx, my + open * 8, mw * 0.35, 0, 7); x.fill();
    }
    x.globalAlpha = 1;

    // blush
    if (f.blush > 0.02) {
      x.globalAlpha = f.blush * 0.8; x.fillStyle = '#FF8FA3';
      for (const side of [-1, 1]) {
        x.beginPath(); x.ellipse(px(side * 19), py(57.5), 9 * UX, 3.6 * UY, 0, 0, 7); x.fill();
      }
      x.globalAlpha = 1;
    }
  }
}

/* ============ PIPO ============ */

const DEG = Math.PI / 180;
const lerp = (a, b, t) => a + (b - a) * t;
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

export class Pipo {
  constructor() {
    this.root = new THREE.Group();          // world placement, scaled to 40u
    this.root.scale.setScalar(0.4);
    this.body = new THREE.Group();          // lean / lie / squash pivot at feet
    this.root.add(this.body);

    const mShell = mat(C.shell, { rough: .5 });
    const mShade = mat(C.shellShade);
    const mJoint = mat(C.joint, { rough: .5 });
    const mArm = mat(C.arm);
    const mBelly = mat(C.belly);

    // torso
    this.torso = box(30, 25, 20, 11, C.shell, { rough: .5 });
    this.torso.position.y = 24.5;
    this.body.add(this.torso);

    const belly = new THREE.Mesh(new THREE.PlaneGeometry(18, 13), mat(C.belly, { rough: .55 }));
    belly.position.set(0, 24.5, 10.3);
    this.body.add(belly);

    this.core = new THREE.Mesh(new THREE.CircleGeometry(5.6, 24), new THREE.MeshStandardMaterial({
      color: C.shell, emissive: C.eye, emissiveIntensity: 1.4, roughness: .4,
    }));
    this.core.position.set(0, 24.5, 10.5);
    this.body.add(this.core);
    this.coreGlow = glowSprite(C.eye, 26, .5);
    this.coreGlow.position.set(0, 24.5, 12);
    this.body.add(this.coreGlow);

    // neck
    const neck = box(10, 6, 9, 2, C.joint);
    neck.position.y = 38;
    this.body.add(neck);

    // head
    this.head = new THREE.Group();
    this.head.position.y = 40;
    this.body.add(this.head);

    const skull = box(64, 50, 42, 21, C.shell, { rough: .48 });
    skull.position.y = 25;
    this.head.add(skull);

    // face
    this.face = new Face();
    this.faceMesh = new THREE.Mesh(
      new THREE.PlaneGeometry(52, 37),
      new THREE.MeshBasicMaterial({ map: this.face.tex, transparent: true, toneMapped: false })
    );
    this.faceMesh.position.set(0, 24.5, 21.4);
    this.head.add(this.faceMesh);

    // ears
    this.ears = [];
    for (const side of [-1, 1]) {
      const g = new THREE.Group();
      g.position.set(side * 33, 25, 0);
      const pod = new THREE.Mesh(new THREE.CylinderGeometry(6.5, 6.5, 7, 20), mShade);
      pod.rotation.z = Math.PI / 2;
      pod.castShadow = true;
      const inner = new THREE.Mesh(new THREE.CylinderGeometry(3.1, 3.1, 7.4, 16),
        new THREE.MeshStandardMaterial({ color: C.shellShade, emissive: C.eye, emissiveIntensity: 1.6, roughness: .4 }));
      inner.rotation.z = Math.PI / 2;
      g.add(pod, inner);
      const gl = glowSprite(C.eye, 14, .5);
      g.add(gl);
      this.head.add(g);
      this.ears.push({ g, inner, gl });
    }

    // antenna
    this.antenna = new THREE.Group();
    this.antenna.position.set(0, 49, -2);
    const stem = new THREE.Mesh(new THREE.CylinderGeometry(1.2, 1.4, 11, 10), mJoint);
    stem.position.y = 5.5;
    this.antenna.add(stem);
    this.bulb = new THREE.Mesh(new THREE.SphereGeometry(4, 18, 14),
      new THREE.MeshStandardMaterial({ color: C.shell, emissive: C.eye, emissiveIntensity: 1.8, roughness: .3 }));
    this.bulb.position.y = 13;
    this.antenna.add(this.bulb);
    this.bulbGlow = glowSprite(C.eye, 30, .6);
    this.bulbGlow.position.y = 13;
    this.antenna.add(this.bulbGlow);
    this.head.add(this.antenna);
    this.antVel = 0; this.antAngle = 0;

    // arms
    this.arms = [];
    for (const side of [-1, 1]) {
      const g = new THREE.Group();
      g.position.set(side * 14, 31, 0);
      const upper = new THREE.Mesh(new THREE.CylinderGeometry(2.6, 2.4, 13, 12), mArm);
      upper.position.y = -6.5;
      upper.castShadow = true;
      const hand = new THREE.Mesh(new THREE.SphereGeometry(3.9, 16, 12), mShade);
      hand.position.y = -13;
      hand.castShadow = true;
      g.add(upper, hand);
      this.body.add(g);
      this.arms.push(g);
    }

    // legs
    this.legs = [];
    this.feet = [];
    for (const side of [-1, 1]) {
      const hip = new THREE.Group();
      hip.position.set(side * 8, 12, 0);
      const shin = new THREE.Mesh(new THREE.CylinderGeometry(3, 3, 9.5, 12), mJoint);
      shin.position.y = -4.75;
      shin.castShadow = true;
      const foot = new THREE.Mesh(new THREE.SphereGeometry(6, 16, 12), mShade);
      foot.scale.set(1, 0.42, 1.15);
      foot.position.set(0, -10.2, 3.5);
      foot.castShadow = true;
      hip.add(shin, foot);
      this.body.add(hip);
      this.legs.push(hip);
      this.feet.push(foot);
    }

    this.pose = {
      bob: 0, squash: 1, stretch: 1, lean: 0, headTilt: 0, headBob: 0,
      armL: 8, armR: 8, legL: 0, legR: 0, spread: 0, sit: 0, lie: 0, liePitch: 0,
      crouch: 0, yaw: 0, headYaw: 0, cross: 0, cover: 0, hold: 0, jump: 0, shake: 0, swing: 0, swingAmt: 0,
    };
    this.cur = { ...this.pose };
    this.prevYaw = 0;
    this.moving = false;
    this.speakT = 0;
    this.energy = 1;
  }

  setMood(mood) {
    const g = MOOD_GLOW[mood] ?? MOOD_GLOW.CURIOUS;
    const col = new THREE.Color(g);
    this.bulb.material.emissive.copy(col);
    this.core.material.emissive.copy(col);
    for (const e of this.ears) { e.inner.material.emissive.copy(col); e.gl.material.color.copy(col); }
    this.bulbGlow.material.color.copy(col);
    this.coreGlow.material.color.copy(col);
    this.glow = g;
  }

  sayFor(seconds) { this.speakT = seconds; }

  update(dt, t, opts = {}) {
    const p = this.pose, c = this.cur;
    const k = 1 - Math.exp(-7 * dt);
    const kf = 1 - Math.exp(-16 * dt);
    for (const key of ['bob', 'squash', 'stretch', 'lean', 'headTilt', 'headBob', 'armL', 'armR',
      'legL', 'legR', 'spread', 'sit', 'lie', 'liePitch', 'crouch', 'cross', 'cover', 'hold', 'jump', 'shake', 'swing', 'swingAmt']) {
      c[key] = lerp(c[key], p[key], p[key] !== undefined && (key === 'bob' || key === 'squash') ? kf : k);
    }
    // yaw spring (underdamped, like the app)
    const stiff = 42, damp = 8.5;
    if (this.yawV === undefined) { this.yawV = 0; this.yawCur = p.yaw; }
    const a = (p.yaw - this.yawCur) * stiff - this.yawV * damp;
    this.yawV += a * dt; this.yawCur += this.yawV * dt;
    this.body.rotation.y = this.yawCur;
    c.headYaw = lerp(c.headYaw, p.headYaw, 1 - Math.exp(-12 * dt));
    this.head.rotation.y = clamp(c.headYaw, -1.2, 1.2);

    // antenna spring driven by acceleration
    const acc = (p.yaw - this.prevYaw) / Math.max(dt, 1e-4);
    this.prevYaw = p.yaw;
    const target = (p.crouch * -30 + Math.sin(t * 1.3) * 5) * DEG;
    const aa = (target - this.antAngle) * 140 - this.antVel * 7.5 - acc * 1.1 * 0.004;
    this.antVel += aa * dt; this.antAngle += this.antVel * dt;
    this.antenna.rotation.z = clamp(this.antAngle, -1.2, 1.2);

    // body transform
    const b = this.body;
    b.position.y = (c.bob + c.jump) - c.sit * 9 - c.crouch * 8;
    b.position.x = c.shake;
    b.rotation.x = -c.lean * DEG;
    b.scale.set(1 + (c.stretch - 1), c.squash, 1 + (c.stretch - 1));
    b.rotation.z = 0;

    // lie: rotate flat about pivot y=30
    if (c.lie > 0.02) {
      b.rotation.x = -c.lie * (Math.PI / 2 - c.liePitch) + -c.lean * DEG * (1 - c.lie);
      b.position.y = lerp(b.position.y, 6 * c.lie + (c.bob) , c.lie);
    }

    this.head.rotation.z = -c.headTilt * DEG * 0.7;
    this.head.rotation.x = (c.headBob - c.headTilt * 0.4) * DEG;

    // legs
    const spreadX = c.spread;
    this.legs[0].position.x = -8 - spreadX;
    this.legs[1].position.x = 8 + spreadX;
    const lift = [c.legL, c.legR];
    const swing = [c.swing * c.swingAmt, -c.swing * c.swingAmt];
    for (let i = 0; i < 2; i++) {
      const leg = this.legs[i];
      leg.position.y = 12 + lift[i] * 0.4;
      leg.rotation.x = swing[i] * DEG - c.sit * 74 * DEG - c.lie * 30 * DEG;
      leg.rotation.z = 0;
    }

    // arms
    for (let i = 0; i < 2; i++) {
      const arm = this.arms[i];
      const raise = (i === 0 ? c.armL : c.armR);
      const side = i === 0 ? -1 : 1;
      let rz = side * raise * DEG;
      let rx = c.swing * c.swingAmt * (i === 0 ? 1 : -1) * 0.9;
      if (c.cross > 0.05) { rz = side * lerp(raise, 16, c.cross) * DEG; rx = lerp(rx, 72 * DEG, c.cross); arm.position.x = side * lerp(14, 6, c.cross); }
      else arm.position.x = side * 14;
      if (c.hold > 0.05) { rx = lerp(rx, 62 * DEG, c.hold); rz = side * lerp(raise, 22, c.hold) * DEG; }
      if (c.cover > 0.05) { rx = lerp(rx, 128 * DEG, c.cover); rz = side * lerp(raise, 26, c.cover) * DEG; arm.position.x = side * lerp(14, 17, c.cover); }
      arm.rotation.set(rx, 0, rz);
    }

    // face
    const sleepy = opts.sleepy;
    this.face.update(dt, !!sleepy, this.speakT > 0);
    if (this.speakT > 0) this.speakT -= dt;

    // glow pulse
    const pulse = 1.3 + Math.sin(t * 2.1) * 0.35;
    this.bulb.material.emissiveIntensity = pulse * (0.7 + this.energy * 0.5);
    this.bulbGlow.material.opacity = 0.35 + 0.25 * Math.sin(t * 2.1);
    this.core.material.emissiveIntensity = pulse * 0.9;
  }

  // convenience: world-space head position (for bubbles)
  headWorld(v) {
    this.head.getWorldPosition(v);
    return v;
  }
}

/* ============ NIB ============ */

export class Nib {
  constructor() {
    this.root = new THREE.Group();
    this.body = new THREE.Group();
    this.root.add(this.body);

    const dome = new THREE.Mesh(
      new THREE.SphereGeometry(6.5, 26, 16, 0, Math.PI * 2, 0, Math.PI / 2),
      mat(C.nibShell, { rough: .5 })
    );
    dome.scale.y = 1.42;
    dome.castShadow = true;
    dome.receiveShadow = true;
    this.dome = dome;
    this.body.add(dome);

    const band = box(13, 2.4, 12, 1.1, C.nibBand, { rough: .55 });
    band.position.y = -0.4;
    this.body.add(band);

    // ears
    this.ears = [];
    for (const side of [-1, 1]) {
      const e = box(1.4, 2.6, 1.4, 0.7, C.nibShade);
      e.position.set(side * 4.4, 8.2, 1.2);
      this.body.add(e);
      this.ears.push(e);
    }

    // eye
    this.eyeGroup = new THREE.Group();
    this.eyeGroup.position.set(0, 5.2, 0);
    const plate = new THREE.Mesh(new THREE.CircleGeometry(2.7, 26),
      new THREE.MeshBasicMaterial({ color: C.screen, toneMapped: false }));
    plate.position.z = 6.05;
    this.pupil = new THREE.Mesh(new THREE.CircleGeometry(1.35, 20),
      new THREE.MeshBasicMaterial({ color: C.eye, toneMapped: false }));
    this.pupil.position.set(0.3, 0, 6.2);
    const spec = new THREE.Mesh(new THREE.CircleGeometry(0.42, 12),
      new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: .85, toneMapped: false }));
    spec.position.set(-0.5, 0.5, 6.32);
    this.lid = new THREE.Mesh(new THREE.PlaneGeometry(6, 3),
      new THREE.MeshBasicMaterial({ color: C.nibShade, transparent: true, opacity: 0, toneMapped: false }));
    this.lid.position.set(0, 1.5, 6.4);
    this.eyeGroup.add(plate, this.pupil, spec, this.lid);
    this.body.add(this.eyeGroup);

    // legs
    this.legs = [];
    for (const side of [-1, 1]) {
      const l = box(1.8, 2.8, 2.4, 0.8, C.nibBand, { rough: .6 });
      l.position.set(side * 2.6, -2.6, 0);
      this.body.add(l);
      this.legs.push(l);
    }

    // tail
    this.tail = new THREE.Group();
    this.tail.position.set(0, 1.6, -5.6);
    const stick = new THREE.Mesh(new THREE.CylinderGeometry(0.3, 0.3, 4.6, 8), mat(C.nibBand));
    stick.rotation.x = Math.PI / 2.4;
    stick.position.z = -2; stick.position.y = 0.4;
    this.tailBob = new THREE.Mesh(new THREE.SphereGeometry(1, 12, 10),
      new THREE.MeshStandardMaterial({ color: C.shell, emissive: C.eye, emissiveIntensity: 1.8, roughness: .3 }));
    this.tailBob.position.set(0, 1.6, -4);
    this.tailGlow = glowSprite(C.eye, 7, .55);
    this.tailGlow.position.copy(this.tailBob.position);
    this.tail.add(stick, this.tailBob, this.tailGlow);
    this.body.add(this.tail);

    this.state = {
      hop: 0, squash: 1, lean: 0, facing: 0, facingNow: 0,
      lookX: 0, lookY: 0, lidOp: 0, blinkT: 0, nextBlink: 1.5 + Math.random() * 3.5,
      tailA: 0, tailV: 0, earUp: 0, shake: 0,
    };
    this.carry = null;
  }

  update(dt, t, cfg) {
    const s = this.state;
    // facing spring
    s.facingNow += (s.facing - s.facingNow) * (1 - Math.exp(-10 * dt));
    this.body.rotation.y = s.facingNow;

    // blink
    s.nextBlink -= dt;
    if (s.nextBlink <= 0) { s.blinkT = 0.14; s.nextBlink = 1.5 + Math.random() * 3.5; }
    let blink = 0;
    if (s.blinkT > 0) { s.blinkT -= dt; blink = Math.sin(Math.PI * clamp(1 - s.blinkT / 0.14, 0, 1)); }

    const lidTarget = cfg.lid ?? (s.blinkT > 0 ? 1 : 0);
    s.lidOp += (lidTarget - s.lidOp) * (1 - Math.exp(-18 * dt));
    this.lid.material.opacity = clamp(s.lidOp, 0, 1);
    this.lid.position.y = 1.5 + 0.4;
    this.lid.scale.y = 1;
    void blink;

    // pupil gaze
    const k = 1 - Math.exp(-12 * dt);
    this.pupil.position.x += ((cfg.lookX || 0) * 1.6 - this.pupil.position.x) * k;
    this.pupil.position.y += ((cfg.lookY || 0) * 1.3 - this.pupil.position.y) * k;

    // body squash / breathing
    let sq = 1 + Math.sin(t * 2.2) * 0.015;
    if (cfg.anim === 'SLEEP') sq = 0.78 + Math.sin(t * 1.4) * 0.025;
    if (cfg.anim === 'SIT') sq = 0.92;
    if (cfg.anim === 'HIDE') sq = 0.70 + Math.sin(t * 30) * 0.02;
    if (cfg.settle > 0) sq *= 1 - 0.12 * Math.sin(Math.PI * cfg.settle);
    s.squash += (sq - s.squash) * (1 - Math.exp(-10 * dt));

    let lift = 0;
    if (cfg.anim === 'HOP') lift = Math.abs(Math.sin(t * 7)) * 5;
    else if (cfg.anim === 'RUN') lift = Math.abs(Math.sin(t * 16)) * 1.25;
    else if (cfg.anim === 'WALK') lift = Math.abs(Math.sin(t * 10)) * 0.6;
    else if (cfg.anim === 'WAG') lift = Math.abs(Math.sin(t * 9)) * 0.6;

    let lean = 0;
    if (cfg.anim === 'STARE') lean = 1.2;
    if (cfg.anim === 'RUN') lean = 1.0;
    s.shake = cfg.anim === 'HIDE' ? Math.sin(t * 40) * 0.25 : 0;

    this.body.position.y = lift + 3.4;
    this.body.position.x = s.shake;
    this.body.scale.set(1, s.squash, 1);
    this.body.rotation.x = lean * 0.06;

    // legs step
    const step = cfg.anim === 'RUN' ? Math.sin(t * 16) : cfg.anim === 'WALK' || cfg.anim === 'HOP' ? Math.sin(t * 10) : 0;
    this.legs[0].position.y = -2.6 + Math.max(0, step) * 0.8;
    this.legs[1].position.y = -2.6 + Math.max(0, -step) * 0.8;

    // tail spring
    let target = Math.sin(t * 1.6) * 10;
    if (cfg.anim === 'WAG') target = Math.sin(t * 18) * 40;
    else if (cfg.anim === 'RUN') target = -25 + Math.sin(t * 14) * 12;
    else if (cfg.anim === 'STARE') target = 35;
    else if (cfg.anim === 'SLEEP' || cfg.anim === 'HIDE') target = -40;
    else if (cfg.anim === 'WALK') target = Math.sin(t * 9) * 18;
    s.tailV += ((target - s.tailV * 0) * 0); // placeholder, spring below
    s.tailV = s.tailV + ((target - s.tailA) * 90 - s.tailV * 7) * dt;
    s.tailA += s.tailV * dt;
    this.tail.rotation.x = s.tailA * DEG * 0.5;
    this.tail.rotation.y = Math.sin(s.tailA * DEG) * 0.8;

    // ears
    const earT = cfg.earUp ?? 0;
    s.earUp += (earT - s.earUp) * (1 - Math.exp(-8 * dt));
    for (let i = 0; i < 2; i++) {
      this.ears[i].position.y = 8.2 + s.earUp * 1.4;
      this.ears[i].rotation.z = (i === 0 ? -1 : 1) * (s.earUp < 0 ? -s.earUp * 0.6 : 0);
    }

    // tail bobble color / carried item
    const bobCol = new THREE.Color(cfg.tailColor ?? C.eye);
    this.tailBob.material.emissive.copy(bobCol);
    this.tailGlow.material.color.copy(bobCol);
    this.tailGlow.material.opacity = cfg.anim === 'SLEEP' ? 0.28 : 0.55;
    void s.lean;
  }
}
