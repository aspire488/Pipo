import * as THREE from 'three';
import { GLTFLoader } from '../vendor/GLTFLoader.js';
import { MOOD_GLOW, glowSprite } from './pipo.js';
import { ScreenFace } from './face.js';

/*
 * Pipo, from the Blender model (art/blender/build_pipo3d.py -> models/pipo.glb).
 * The GLB's empties are the rig: Body, Head, Antenna, ArmL/R, LegL/R (glTF drops the dots).
 * Pose fields and their units are the app's Rig (Pipo-local, 100 units tall, degrees).
 */

const DEG = Math.PI / 180;
const lerp = (a, b, t) => a + (b - a) * t;
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));

/** Suit looks. One armor mesh set; each mark is a material look + which extras glow. */
export const SUITS = {
  mk0: { red: 0xC49A6C, gold: 0xE8DCC4, core: 0xE0605A, coreGlow: 0, rough: 0.95, metal: 0, thrusters: false, label: 'Mk 0 (cardboard)' },
  1: { red: 0xB3202A, gold: 0xE2B24A, core: 0xDDFBFF, coreGlow: 1.4, rough: 0.36, metal: 0.45, thrusters: false, label: 'Mk I' },
  2: { red: 0xA81E2E, gold: 0xD9A23A, core: 0xDDFBFF, coreGlow: 1.6, rough: 0.34, metal: 0.5, thrusters: true, label: 'Mk II' },
  3: { red: 0x9E1B33, gold: 0xF0C25A, core: 0x9FF3FF, coreGlow: 2.2, rough: 0.3, metal: 0.55, thrusters: true, label: 'Mk III' },
};
export function suitKey(mk, cardboard) { return mk <= 0 ? null : cardboard ? 'mk0' : String(Math.min(3, mk)); }

let gltfPromise = null;
export function loadPipoModel(url = 'models/pipo.glb') {
  gltfPromise ??= new GLTFLoader().loadAsync(url);
  return gltfPromise;
}

export class PipoModel {
  /** @param gltf loaded pipo.glb; @param opts.armorOnly a suit standing on its own (the dock). */
  constructor(gltf, opts = {}) {
    const src = gltf.scene.getObjectByName('Pipo');
    this.model = src.clone(true);
    // armor materials are per-instance: the dock's suit and the worn suit can differ
    this.armorMats = {};
    this.model.traverse(o => {
      if (!o.isMesh) return;
      o.castShadow = true; o.receiveShadow = true;
      const n = o.material.name;
      if (n.startsWith('Armor_')) {
        this.armorMats[n] ??= o.material.clone();
        o.material = this.armorMats[n];
      }
    });

    this.root = new THREE.Group();
    this.root.scale.setScalar(0.4);
    this.root.add(this.model);
    const get = n => this.model.getObjectByName(n);
    this.body = get('Body');
    this.head = get('Head');
    this.antenna = get('Antenna');
    this.arms = [get('ArmL'), get('ArmR')];
    this.legs = [get('LegL'), get('LegR')];
    this.armor = [];
    this.thrusters = [];
    this.model.traverse(o => {
      if (o.name.startsWith('MK1_')) this.armor.push(o);
      if (o.name.startsWith('MK1_Thruster')) this.thrusters.push(o);
    });
    this.armorOnly = !!opts.armorOnly;
    if (this.armorOnly) {
      // only the suit: hide every piece of Pipo himself
      this.model.traverse(o => { if (o.isMesh && !o.name.startsWith('MK1_')) o.visible = false; });
    }

    // glow parts recolor with his mood
    this.glowMat = null;
    this.model.traverse(o => { if (o.isMesh && o.material.name === 'Pipo_Glow') { this.glowMat ??= o.material.clone(); o.material = this.glowMat; } });

    if (!this.armorOnly) {
      // face: a lit screen inset in the Blender bezel (casing -> graphite bezel -> screen)
      this.face = new ScreenFace();
      this.faceMesh = new THREE.Mesh(new THREE.PlaneGeometry(45.6, 31),
        new THREE.MeshBasicMaterial({ map: this.face.tex, transparent: true, toneMapped: false }));
      this.faceMesh.position.set(0, 24.5, 21.85);
      this.faceMesh.renderOrder = 2;   // always over the bezel: no z-fighting streaks
      this.faceMesh.material.depthWrite = false;
      this.head.add(this.faceMesh);
      this.bulbGlow = glowSprite(0x8FF5E2, 16, 0.3);
      this.bulbGlow.position.y = 13;
      this.antenna.add(this.bulbGlow);
      this.coreGlow = glowSprite(0x8FF5E2, 12, 0.22);
      this.coreGlow.position.set(0, 24.5, 13);
      this.body.add(this.coreGlow);
    }
    // thruster flames (Mk II+ when flying)
    this.flames = this.legs.map(leg => {
      const s = glowSprite(0x9FF3FF, 14, 0);
      s.position.set(0, -15, 3.6);
      leg.add(s);
      return s;
    });

    this.pose = {
      bob: 0, squash: 1, stretch: 1, lean: 0, headTilt: 0, headBob: 0,
      armL: 8, armR: 8, legL: 0, legR: 0, spread: 0, sit: 0, lie: 0, liePitch: 0,
      crouch: 0, yaw: 0, headYaw: 0, cross: 0, cover: 0, hold: 0, jump: 0, shake: 0, swing: 0, swingAmt: 0,
    };
    this.cur = { ...this.pose };
    this.speakT = 0;
    this.energy = 1;
    this.antVel = 0; this.antAngle = 0; this.prevYaw = 0;

    // equipment: one source of truth, set only through equip()
    this.suit = null;           // null | 'mk0' | '1' | '2' | '3'
    this.suitT = 1;             // 0..1 transition progress
    this.suitDir = 0;           // +1 putting on, -1 taking off
    this.flying = false;
    for (const a of this.armor) { a.userData.base = a.scale.clone(); a.visible = false; }
  }

  setMood(mood) {
    const col = new THREE.Color(MOOD_GLOW[mood] ?? MOOD_GLOW.CURIOUS);
    if (this.glowMat) { this.glowMat.emissive.copy(col); this.glowMat.color.copy(col).multiplyScalar(0.25); }
    this.bulbGlow?.material.color.copy(col);
    this.coreGlow?.material.color.copy(col);
    this.moodColor = col;
  }

  sayFor(seconds) { this.speakT = seconds; }

  /**
   * Put a suit on (key from suitKey) or take it off (null). Idempotent: equipping the suit he
   * already wears does nothing, so repeated taps can't stack meshes or restart the animation.
   */
  equip(key, instant = false) {
    if (key === this.suit && (this.suitDir === 0 || instant)) return false;
    if (key) this._look(SUITS[key]);
    const wasOn = this.suit;
    this.suit = key;
    if (instant) {
      this.suitT = 1; this.suitDir = 0;
      for (const a of this.armor) { a.visible = !!key; a.scale.copy(a.userData.base); }
      return true;
    }
    this.suitDir = key ? 1 : -1;
    this.suitT = 0;
    if (key && !wasOn) for (const a of this.armor) { a.visible = true; a.scale.setScalar(0.001); }
    return true;
  }

  _look(s) {
    const red = this.armorMats.Armor_Red, gold = this.armorMats.Armor_Gold, core = this.armorMats.Armor_Core;
    red?.color.setHex(s.red); gold?.color.setHex(s.gold);
    if (red) { red.roughness = s.rough; red.metalness = s.metal; }
    if (gold) { gold.roughness = Math.max(s.rough, 0.3); gold.metalness = s.metal ? 0.85 : 0; }
    if (core) { core.color.setHex(s.core); core.emissive.setHex(s.core); core.emissiveIntensity = s.coreGlow; }
    for (const th of this.thrusters) th.visible = s.thrusters;
  }

  update(dt, t, opts = {}) {
    const p = this.pose, c = this.cur;
    const k = 1 - Math.exp(-7 * dt);
    const kf = 1 - Math.exp(-16 * dt);
    for (const key of ['bob', 'squash', 'stretch', 'lean', 'headTilt', 'headBob', 'armL', 'armR',
      'legL', 'legR', 'spread', 'sit', 'lie', 'liePitch', 'crouch', 'cross', 'cover', 'hold', 'jump', 'shake', 'swing', 'swingAmt']) {
      c[key] = lerp(c[key], p[key], key === 'bob' || key === 'squash' || key === 'armL' || key === 'armR' || key === 'legL' || key === 'legR' ? kf : k);
    }
    // yaw spring (underdamped, like the app)
    if (this.yawV === undefined) { this.yawV = 0; this.yawCur = p.yaw; }
    const a = (p.yaw - this.yawCur) * 42 - this.yawV * 8.5;
    this.yawV += a * dt; this.yawCur += this.yawV * dt;
    this.body.rotation.y = this.yawCur;
    c.headYaw = lerp(c.headYaw, p.headYaw, 1 - Math.exp(-12 * dt));
    this.head.rotation.y = clamp(c.headYaw, -1.2, 1.2);

    // antenna spring driven by turning
    const acc = (p.yaw - this.prevYaw) / Math.max(dt, 1e-4);
    this.prevYaw = p.yaw;
    const target = (c.crouch * -30 + Math.sin(t * 1.3) * 5 + (opts.antenna || 0)) * DEG;
    const aa = (target - this.antAngle) * 140 - this.antVel * 7.5 - acc * 0.0044;
    this.antVel += aa * dt; this.antAngle += this.antVel * dt;
    this.antenna.rotation.z = clamp(this.antAngle, -1.2, 1.2);

    const b = this.body;
    b.position.y = (c.bob + c.jump) - c.sit * 9 - c.crouch * 8;
    b.position.x = c.shake;
    b.rotation.x = -c.lean * DEG;
    b.scale.set(c.stretch, c.squash, c.stretch);
    if (c.lie > 0.02) {
      b.rotation.x = -c.lie * (Math.PI / 2 - c.liePitch) + -c.lean * DEG * (1 - c.lie);
      b.position.y = lerp(b.position.y, 6 * c.lie + c.bob, c.lie);
    }
    this.head.position.y = 40;
    this.head.rotation.z = -c.headTilt * DEG * 0.7;
    this.head.rotation.x = (c.headBob - c.headTilt * 0.4) * DEG;

    // legs
    this.legs[0].position.x = -8 - c.spread;
    this.legs[1].position.x = 8 + c.spread;
    const lift = [c.legL, c.legR];
    const swing = [c.swing * c.swingAmt, -c.swing * c.swingAmt];
    for (let i = 0; i < 2; i++) {
      this.legs[i].position.y = 12 + lift[i] * 0.4;
      this.legs[i].rotation.x = swing[i] * DEG - c.sit * 74 * DEG - c.lie * 30 * DEG;
    }
    // arms
    for (let i = 0; i < 2; i++) {
      const arm = this.arms[i];
      const raise = i === 0 ? c.armL : c.armR;
      const side = i === 0 ? -1 : 1;
      let rz = side * raise * DEG;
      let rx = c.swing * c.swingAmt * (i === 0 ? 1 : -1) * 0.9 * DEG;
      arm.position.x = side * 14;
      if (c.cross > 0.05) { rz = side * lerp(raise, 16, c.cross) * DEG; rx = lerp(rx, 72 * DEG, c.cross); arm.position.x = side * lerp(14, 6, c.cross); }
      if (c.hold > 0.05) { rx = lerp(rx, 62 * DEG, c.hold); rz = side * lerp(raise, 22, c.hold) * DEG; }
      if (c.cover > 0.05) { rx = lerp(rx, 128 * DEG, c.cover); rz = side * lerp(raise, 26, c.cover) * DEG; arm.position.x = side * lerp(14, 17, c.cover); }
      arm.rotation.set(rx, 0, rz);
    }

    this._updateSuit(dt, t);

    if (this.face) {
      this.face.update(dt, !!opts.sleepy, this.speakT > 0 || !!opts.talking);
      if (this.speakT > 0) this.speakT -= dt;
      // a slow, restrained pulse: alive, not neon
      const pulse = 0.85 + Math.sin(t * 1.6) * 0.15;
      if (this.glowMat) this.glowMat.emissiveIntensity = pulse * (0.45 + this.energy * 0.2) * (opts.glow ?? 1);
      this.bulbGlow.material.opacity = (0.2 + 0.1 * Math.sin(t * 1.6)) * (opts.glow ?? 1);
      this.coreGlow.material.opacity = this.suit ? 0 : 0.18;
    }
  }

  _updateSuit(dt, t) {
    if (this.suitDir !== 0) {
      this.suitT = Math.min(1, this.suitT + dt / 0.9);
      const n = this.armor.length;
      this.armor.forEach((a, i) => {
        // pieces snap on one after another, feet first; off in reverse
        const order = this.suitDir > 0 ? i / n : 1 - i / n;
        const local = clamp((this.suitT - order * 0.55) / 0.45, 0, 1);
        const e = this.suitDir > 0 ? 1 - Math.pow(1 - local, 3) * Math.cos(local * 6) : 1 - local;
        a.scale.copy(a.userData.base).multiplyScalar(Math.max(0.001, e));
      });
      if (this.suitT >= 1) {
        if (this.suitDir < 0) for (const a of this.armor) { a.visible = false; a.scale.copy(a.userData.base); }
        this.suitDir = 0;
      }
    }
    const s = this.suit && SUITS[this.suit];
    const flame = this.flying && s?.thrusters ? 0.85 + Math.sin(t * 40) * 0.15 : 0;
    for (const f of this.flames) { f.material.opacity = flame; f.scale.setScalar(12 + flame * 6); }
  }

  headWorld(v) { this.head.getWorldPosition(v); return v; }
}
