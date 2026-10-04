import * as THREE from 'three';
import { Pipo, Nib, MOOD_GLOW } from './pipo.js';

const rnd = (a, b) => a + Math.random() * (b - a);
const pick = a => a[(Math.random() * a.length) | 0];
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
const lerp = (a, b, t) => a + (b - a) * t;
const dist2 = (ax, az, bx, bz) => Math.hypot(ax - bx, az - bz);

const L = {
  firstWake: ['Hi.', '...I\u2019m Pipo.', 'I live here. This is my room.', 'You can poke me. Gently.'],
  woken: ['...what. I was sleeping.', 'Mmh? Oh. You.', '...I was dreaming about screws.', 'Five more... okay, I\u2019m up.'],
  ignoreSleep: ['...mmh.', '...zzz.', '...no.'],
  sleepMumble: ['...five more minutes.', '...mmh. not now.', 'zz... ...the screws are talking...', '...I\u2019m not asleep. I\u2019m buffering.'],
  lieDown: ['I\u2019m looking at the ceiling. It\u2019s doing nothing. Same.', 'Floor time. Bed time. Same idea.', 'Don\u2019t mind me. I\u2019m resting my everything.'],
  morning: ['Morning. The sun is back.', 'Good morning. I\u2019m up. Mostly.'],
  lateNight: ['It\u2019s really late. Why are we awake?', 'It\u2019s nighttime. My eyes are doing the heavy thing.'],
  charged: ['Fully charged. Mostly.', 'Ahh. Electrons.', 'I feel like a new robot.'],
  chargingStart: ['Ahhh. Power.', 'Ooh. Power.', 'Charging time! Mine too.'],
  pokeHi: ['Hi!', 'Hehe.', 'That tickles.', 'Oh! Hi.', 'Beep.'],
  pokeMid: ['Hey.', 'Why.', 'That\u2019s my face.', 'Rude.'],
  pokeMany: ['Stop poking me.', 'Okay. That\u2019s enough poking.', 'I will remember this.', 'Hey. HEY.'],
  pokeMax: ['I\u2019m not here.', 'This is a hiding spot. You can\u2019t see me.', 'Pipo is unavailable. Leave a beep.'],
  pokeGrump: ['What.', 'No.', 'Hmph.'],
  pokeHead: ['My antenna! It\u2019s sensitive.', 'Boing. Don\u2019t do that. Do it again.', 'Hey. That\u2019s my thinking part.'],
  pokeFeet: ['My feet! They\u2019re ticklish. Robots can be ticklish.', 'Hey. Those are my walking parts.'],
  thoughts: [
    'What if doors are just walls that gave up?',
    'I think the plant is judging me.',
    'If I had a tail, I\u2019d wag it at the toaster.',
    'Do you think clouds know they\u2019re clouds?',
    'I\u2019ve decided my favorite number is 7. No reason. It just seems nice.',
    'What if I\u2019m the one keeping the phone alive?',
    'Sometimes I count my screws. It\u2019s calming.',
    'I had a dream I was a toaster. It was fine.',
    'Is a sandwich a robot? Think about it.',
    'I\u2019m thinking about building a smaller me. For company.',
    'I have an extremely questionable idea.',
    'The window is my favorite screen.',
  ],
  computer: ['I looked at the internet. It looked back.', 'I typed my name 400 times. It\u2019s still my name.', 'The computer says I\u2019m doing great.', 'I made a spreadsheet of my screws.'],
  build: ['Science.', 'Hm. Interesting. Bad, but interesting.', 'If I connect this to this... no.', 'I\u2019m very close to something.', 'I\u2019m okay!', 'Small explosion. Very small.'],
  arcade: ['New high score! Nobody saw it.', 'The arcade cheats.', 'I almost won. Against myself.'],
  plant: ['The plant grew. I think. I measured with my eyes.', 'Good job, plant.', 'I told the plant a joke. No reaction.', 'The plant and I have an understanding.'],
  drawings: ['I drew you. You\u2019re a circle. It\u2019s accurate.', 'This one is the sun. It has a face. Everything has a face.'],
  explore: ['Nothing under the bed. Again.', 'I explored. The room is still here.', 'I found dust. Regular dust.', 'Hm. I thought I heard something.'],
  toyDone: ['The ball and I are friends again.', 'I won against the ball.', 'Ball: 0. Pipo: 1.'],
  ball: ['Wait. Did the ball just move?', 'Ball! BALL.', 'The ball is looking at me.'],
  weather: {
    clear: ['It\u2019s nice out.', 'Look at that sky.'],
    cloudy: ['Cloudy. The sky is thinking.', 'That cloud looks like a screw.'],
    rain: ['It\u2019s raining. I like watching the drops race.', 'Rain. The window is crying. Happy crying.', 'The drop on the left is winning.'],
  },
  vain: ['Antenna: straight. Screen: shiny. Good.', 'Looking good. Looking VERY good.', 'Hello, handsome.', 'Smudge on my screen. ...fixed.', 'Just checking I\u2019m still me. I am.'],
  mirrorOdd: ['...did my reflection just wave? I didn\u2019t wave.', 'It was a beat behind me. Mirrors aren\u2019t a beat behind.', 'That wasn\u2019t me. That was ME, but... not me.', 'The other me. In there. Hi?'],
  reply: ['What is it, Nib? ...there\u2019s nothing there.', 'Nib? What are you looking at? ...Nib?', 'There\u2019s nothing out there. Right?'],
  hideFound: ['NO. How did you find me?', 'Okay. Okay. You found me. Good eyes.', 'BOO! ...you already saw me. Never mind.'],
  hideNotFound: ['You didn\u2019t find me. I win. Hide and seek champion.', 'Did you even look? I was RIGHT there.', 'I\u2019ve been hiding for ages. I got bored. Still counts.'],
  hideEmbarrassed: ['I\u2019m not hiding. I\u2019m... resting behind here.', 'Don\u2019t look at me. Something happened. It\u2019s fine.'],
  nibHi: ['Hi!', 'Hiii!', 'Nib here!', 'Oh! Hi!'],
  nibMirror: ['Mirror WRONG. Other Pipo in there!', 'Glass Pipo waved. Real Pipo no wave. Nib SAW.', 'Nib no like mirror. Mirror looks back.', 'Mirror-Pipo blinks late. Nib counts. Nib knows.', 'Glass is a door. Nib not going in.'],
  nibTells: ['Pipo! Pipo! Mirror-Pipo moved. By ITSELF!', 'Pipo! The glass one waved at Nib. Not you. HIM.', 'Pipo, listen! Other Pipo in mirror. Same face. Different.', 'Pipo! Mirror has a room in it. Not OUR room!'],
  nibTold: ['TOLD you! Nib TOLD you!', 'Nib saw! Nib SAW it!', 'See?! Glass Pipo!'],
  nibDots: ['...', 'Hmm.', 'Nib is thinking. Loudly.'],
};

const HIDE_SPOTS = [[22, 104], [276, 112], [208, 118], [74, 122]];

const IDLE_TABLE = [
  ['think', 3], ['tv', 2], ['computer', 2], ['build', 2], ['arcade', 1.5],
  ['fridge', 1.5], ['plant', 1.5], ['drawings', 1], ['window', 1.5],
  ['kick', 2], ['mirror', 1.6], ['door', 1], ['charge', 1.4], ['wander', 3],
];

const ACTS = {
  think: {
    mood: 'THOUGHTFUL',
    pose: () => ({ armR: 55, hold: .55, headBob: 5, headTilt: -6 }),
    steps: b => [
      { k: 'say', pool: L.thoughts },
      { k: 'do', key: 'think' }, { k: 'wait', t: rnd(2.6, 4) },
      { k: 'do', key: null },
    ],
  },
  tv: {
    mood: 'RELAXED',
    pose: () => ({ headBob: -3 }),
    on: b => { b.world.tvOn = 1; },
    off: b => { b.world.tvOn = 0; },
    steps: b => [
      { k: 'go', to: b.standOf('tv') }, { k: 'wait', t: 0.4 },
      { k: 'do', key: 'tv' }, { k: 'wait', t: rnd(7, 12) },
      { k: 'fn', fn: b => { b.world.tvOn = 0; } },
      { k: 'do', key: null },
    ],
  },
  computer: {
    mood: 'CURIOUS',
    pose: () => ({ hold: .72, headBob: 6, armL: 18, armR: 18 }),
    steps: b => [
      { k: 'go', to: b.standOf('desk') }, { k: 'fn', fn: b => { b.world.monOn = 1; } },
      { k: 'say', pool: L.computer }, { k: 'do', key: 'computer' }, { k: 'wait', t: rnd(6, 10) },
      { k: 'fn', fn: b => { b.world.monOn = 0; } }, { k: 'do', key: null },
    ],
  },
  build: {
    mood: 'FOCUSED',
    pose: () => ({ hold: .6, headBob: 8, armL: 26, armR: 26 }),
    steps: b => [
      { k: 'go', to: b.standOf('workbench') }, { k: 'say', pool: L.build },
      { k: 'do', key: 'build' }, { k: 'wait', t: rnd(6, 10) }, { k: 'do', key: null },
    ],
  },
  arcade: {
    mood: 'EXCITED',
    pose: () => ({ hold: .5, headBob: 4, armL: 30, armR: 30 }),
    steps: b => [
      { k: 'go', to: b.standOf('arcade') }, { k: 'fn', fn: b => { b.world.arcOn = 1; } },
      { k: 'do', key: 'arcade' }, { k: 'wait', t: rnd(6, 11) },
      { k: 'fn', fn: b => { b.world.arcOn = 0; } }, { k: 'say', pool: L.arcade },
      { k: 'do', key: null },
    ],
  },
  fridge: {
    mood: 'PLAYFUL',
    pose: () => ({ hold: .35, headBob: 3 }),
    steps: b => [
      { k: 'go', to: b.standOf('fridge') }, { k: 'fn', fn: b => { b.world.fridgeGoal = 1; } },
      { k: 'wait', t: 2.2 }, { k: 'fn', fn: b => { b.world.fridgeGoal = 0; b.world.cookGoal = 1; } },
      { k: 'say', pool: L.build }, { k: 'wait', t: rnd(3, 5) },
      { k: 'fn', fn: b => { b.world.cookGoal = 0; } }, { k: 'do', key: null },
    ],
  },
  plant: {
    mood: 'CALM',
    pose: () => ({ headBob: 10, armR: 30 }),
    steps: b => [
      { k: 'go', to: b.standOf('plant') }, { k: 'say', pool: L.plant },
      { k: 'do', key: 'plant' }, { k: 'wait', t: rnd(3, 5) }, { k: 'do', key: null },
    ],
  },
  drawings: {
    mood: 'PROUD',
    expr: 'PROUD',
    pose: () => ({ headBob: -14, armL: 16 }),
    steps: b => [
      { k: 'go', to: b.standOf('drawings') }, { k: 'say', pool: L.drawings },
      { k: 'do', key: 'drawings' }, { k: 'wait', t: rnd(3, 5) }, { k: 'do', key: null },
    ],
  },
  window: {
    mood: 'CURIOUS',
    pose: () => ({ headBob: -8, armR: 12 }),
    steps: b => [
      { k: 'go', to: b.standOf('window') }, { k: 'fn', fn: b => b.sayWeather() },
      { k: 'do', key: 'window' }, { k: 'wait', t: rnd(4, 7) }, { k: 'do', key: null },
    ],
  },
  door: {
    mood: 'CURIOUS',
    pose: () => ({ headBob: 4 }),
    steps: b => [
      { k: 'go', to: b.standOf('door') }, { k: 'fn', fn: b => { b.world.doorGoal = 1; } },
      { k: 'say', pool: L.explore }, { k: 'wait', t: 3 },
      { k: 'fn', fn: b => { b.world.doorGoal = 0; } }, { k: 'do', key: null },
    ],
  },
  charge: {
    mood: 'RELAXED',
    pose: () => ({ armL: 14, armR: 14, headBob: 6 }),
    steps: b => [
      { k: 'go', to: b.standOf('charger') }, { k: 'fn', fn: b => { b.world.charging = 1; b.say(L.chargingStart); } },
      { k: 'do', key: 'charge' }, { k: 'wait', t: rnd(5, 8) },
      { k: 'fn', fn: b => { b.world.charging = 0; b.pipo.energy = 1; b.say(L.charged); } },
      { k: 'do', key: null },
    ],
  },
  kick: {
    mood: 'PLAYFUL',
    pose: t => ({ legR: Math.sin(t * 6) * 14, lean: 6, headBob: 4 }),
    steps: b => [
      { k: 'go', to: b.ballSpot() }, { k: 'say', pool: L.ball },
      { k: 'do', key: 'kick' }, { k: 'wait', t: 0.5 },
      { k: 'fn', fn: b => b.kickBall() }, { k: 'wait', t: 1.2 },
      { k: 'say', pool: L.toyDone }, { k: 'do', key: null },
    ],
  },
  mirror: { pose: () => ({}), steps: b => b.mirrorSteps() },
  mirrorIdle: {
    pose: () => ({ headBob: -4, armR: 14, armL: 10 }),
    steps: b => [{ k: 'wait', t: rnd(2.5, 4) }],
  },
  wander: {
    mood: 'RELAXED',
    pose: () => ({}),
    steps: b => [{ k: 'go', to: b.roamSpot() }, { k: 'wait', t: rnd(1.5, 3.5) }],
  },
};

const STAND = {};
const BASE = {
  bob: 0, squash: 1, stretch: 1, lean: 0, headTilt: 0, headBob: 0,
  armL: 8, armR: 8, legL: 0, legR: 0, spread: 0, sit: 0, lie: 0, liePitch: 0,
  crouch: 0, cross: 0, cover: 0, hold: 0, jump: 0, shake: 0, swing: 0, swingAmt: 0,
};

export class Brain {
  constructor(opts) {
    this.world = opts.world;
    this.scene = opts.scene;
    this.rig = opts.rig;
    this.pipo = opts.pipo;
    this.nib = opts.nib;
    this.bubble = opts.say;
    this.search = opts.search;
    this.searching = opts.searching;
    this.camPos = opts.camPos;

    this.time = 0;
    this.seq = null;
    this.nav = null;
    this.actPose = null;
    this.actKey = null;
    this.actT = 0;
    this.pose = this.pipo.pose;
    this.pose.yaw = 0.3;
    this.pose.headYaw = 0;
    this.pipo.root.position.set(120, 0, 70);
    this.nib.root.position.set(127, 0, 74);
    this.mumbleT = 6;
    this._exprKey = '';
    this.idleT = rnd(4, 8);
    this.lastKey = null;
    this.pokeT = 0;
    this.pokeN = 0;
    this.jumpT = 0;
    this.walkPhase = 0;
    this.lineQ = [];
    this.qWait = 0;
    this.mood = 'CURIOUS';
    this.expr = null;
    this.moodT = 0;
    this.hiding = false;
    this.hideResult = null;
    this.hideSpot = null;
    this.hideStarted = 0;
    this.pendingFind = false;
    this.sleepState = null;
    this.sleepT = 0;
    this.lieK = 0;
    this.climbY = 0;
    this.rootRotX = 0;
    this.rootRotY = 0;
    this.wakeFrom = null;
    this.firstDone = false;
    this.wasMorning = false;
    this.wasNight = false;
    this.mirrorOddUntil = -1;
    this.nibState = { mode: null, timer: 0, target: null, toldOnce: false, lookT: 0 };
    this.nibCfg = { anim: 'WALK', lid: 0, earUp: 0 };
    this.pipoOpts = { sleepy: false };
    this.petT = 0;
    this.talkT = 0;
    this.footZ = 44;
    this._v = new THREE.Vector3();

    this.dbl = new Pipo();
    this.dbl.root.scale.setScalar(0.22);
    this.dbl.root.position.set(274, 6, -6);
    this.dbl.root.rotation.y = 0;
    this.dbl.setMood('CURIOUS');
    this.scene.add(this.dbl.root);

    for (const rec of this.world.interactables) {
      if (rec.stand) STAND[rec.id] = { x: rec.stand.x, z: rec.stand.z, face: rec.face ?? Math.PI };
    }
    this.world.mirrorMystery = false;
  }

  standOf(id) { return STAND[id] || { x: 150, z: 60, face: 0 }; }
  ballSpot() {
    const b = this.world.ball;
    const x = clamp(b.position.x + 7, 20, 276);
    const z = clamp(b.position.z + 7, 30, 130);
    return { x, z, face: Math.atan2(b.position.x - x, b.position.z - z) };
  }
  roamSpot() {
    return { x: rnd(30, 270), z: rnd(50, 126), face: rnd(-0.6, 0.6) };
  }

  say(pool) {
    const t = Array.isArray(pool) ? pick(pool) : pool;
    this.qLine('pipo', t);
    return t;
  }
  sayNib(pool) {
    const t = Array.isArray(pool) ? pick(pool) : pool;
    this.qLine('nib', t);
    return t;
  }
  sayWeather() {
    const w = this.world.env.weather;
    if (Math.random() < 0.65) this.say(L.weather[w] || L.weather.clear);
  }
  qLine(who, text) {
    if (!text) return;
    this.lineQ.push({ who, text });
    if (this.lineQ.length > 6) this.lineQ.shift();
  }

  firstWake() {
    if (this.firstDone) return;
    this.firstDone = true;
    for (const t of L.firstWake) this.qLine('pipo', t);
    this.setMood('HAPPY');
    this.setExpr('HAPPY');
  }

  setMood(m) {
    if (this.mood === m) return;
    this.mood = m;
    this.pipo.setMood(m);
    this.moodT = 1.2;
  }
  setExpr(e) { this.expr = e; }

  /* ---------------- sequencer ---------------- */

  startSeq(key, steps, onEnd) {
    this.clearFlags();
    this.seq = { key, steps, i: -1, s: { t: 0, started: false }, onEnd };
    this.actPose = null; this.actKey = null;
    this.nextStep();
  }
  clearSeq() {
    if (this.seq && this.seq.onEnd) this.seq.onEnd(this);
    this.seq = null;
    this.actPose = null; this.actKey = null;
  }
  clearFlags() {
    const w = this.world;
    w.tvOn = 0; w.monOn = 0; w.arcOn = 0;
    w.fridgeGoal = 0; w.cookGoal = 0; w.doorGoal = 0; w.charging = 0;
    w.mirrorMystery = false;
  }
  nextStep() {
    const q = this.seq;
    if (!q) return;
    q.i++;
    q.s.t = 0; q.s.started = false;
    if (q.i >= q.steps.length) {
      const oe = q.onEnd;
      this.seq = null;
      this.actPose = null; this.actKey = null;
      if (oe) oe(this);
      return;
    }
    const st = q.steps[q.i];
    if (st.k === 'go') { this.goto(st.to.x, st.to.z, st.speed, st.to.face); }
    else if (st.k === 'fn') { st.fn(this); this.nextStep(); return; }
    else if (st.k === 'say') { this.say(st.pool); }
    else if (st.k === 'mood') { this.setMood(st.v); }
    else if (st.k === 'expr') { this.setExpr(st.v); }
    else if (st.k === 'do') {
      this.actKey = st.key;
      this.actPose = st.key ? ACTS[st.key] : null;
      this.actT = 0;
      if (st.key && ACTS[st.key] && ACTS[st.key].expr) this.setExpr(ACTS[st.key].expr);
      if (st.key && ACTS[st.key] && ACTS[st.key].mood) this.setMood(ACTS[st.key].mood);
      if (st.dur !== undefined && st.dur > 0) return;
      this.nextStep(); return;
    }
  }
  stepTick(dt) {
    const q = this.seq;
    if (!q) return;
    this.actT += dt;
    const st = q.steps[q.i];
    if (!st) return;
    q.s.t += dt;
    let done = false;
    switch (st.k) {
      case 'go': done = !this.nav; break;
      case 'wait': done = q.s.t >= st.t; break;
      case 'until': done = q.s.t >= (st.min || 0) && st.test(this, q.s); break;
      case 'say':
        if (!q.s.started) { q.s.started = true; }
        done = q.s.t >= 0.01;
        break;
      default: done = true;
    }
    if (done) this.nextStep();
  }

  /* ---------------- navigation ---------------- */

  goto(x, z, speed, face) {
    const p = this.pipo.root.position;
    const pts = [];
    const dx = x - p.x;
    const minZ = Math.min(p.z, z);
    if (Math.abs(dx) > 60 && minZ < 46) {
      pts.push({ x: (p.x + x) / 2, z: 58 });
    }
    pts.push({ x, z });
    this.nav = { pts, i: 0, speed: speed || 17, face: face };
  }
  stopNav() { this.nav = null; }
  navTick(dt) {
    const nav = this.nav;
    if (!nav) return;
    const p = this.pipo.root.position;
    const t = nav.pts[nav.i];
    const d = dist2(p.x, p.z, t.x, t.z);
    if (d < 3.4) {
      nav.i++;
      if (nav.i >= nav.pts.length) {
        this.nav = null;
        if (nav.face !== undefined && nav.face !== null) this.pose.yaw = nav.face;
        this.pose.headYaw = 0;
        return;
      }
      return;
    }
    const sp = nav.speed * dt;
    const nx = p.x + ((t.x - p.x) / d) * sp;
    const nz = p.z + ((t.z - p.z) / d) * sp;
    p.x = clamp(nx, 14, 282);
    p.z = clamp(nz, this.sleepState === 'climb' ? 18 : 26, 134);
    this.pose.yaw = Math.atan2(t.x - p.x, t.z - p.z);
    this.walkPhase += dt * (nav.speed > 25 ? 11 : 7.5);
    this.moving = true;
  }

  /* ---------------- hide & seek ---------------- */

  find() {
    if (this.hiding) { this.popHide('found'); this.rig.startSearch(this.pipo.root.position, { close: true }); return; }
    if (this.sleepState) {
      this.rig.startSearch(this.pipo.root.position, { close: false });
      this.wake('find');
      this.pendingFind = true;
      return;
    }
    this.rig.startSearch(this.pipo.root.position, { close: false });
    if (!this.seq && !this.nav) this.startHide();
  }
  startHide() {
    const p = this.pipo.root.position;
    let best = HIDE_SPOTS[0], bd = -1;
    for (const s of HIDE_SPOTS) {
      const d = dist2(p.x, p.z, s[0], s[1]);
      if (d > bd) { bd = d; best = s; }
    }
    this.hideSpot = best;
    this.hideResult = null;
    this.startSeq('hide', [
      { k: 'go', to: { x: best[0], z: best[1], face: rnd(-1, 1) }, speed: 34 },
      { k: 'fn', fn: b => { b.hiding = true; b.hideStarted = b.time; b.clearFlags(); b.setMood('MISCHIEVOUS'); b.setExpr('MISCHIEF'); } },
      { k: 'until', min: 3.4, test: b => (b.hideResult !== null && !b.searching()) || b.time - b.hideStarted > 45 },
      { k: 'fn', fn: b => b.popHide() },
    ]);
  }
  hideOn() { this.hiding = true; this.hideResult = null; this.hideStarted = this.time; }
  noteCameraTouch() {
    if (this.hiding && this.hideResult === null && this.time - this.hideStarted > 1.2) this.hideResult = 'notfound';
  }
  popHide(mode) {
    if (!this.hiding) return;
    this.hiding = false;
    const found = mode === 'found' || (mode !== 'abort' && this.hideResult === 'found');
    this.clearSeq();
    this.stopNav();
    if (mode === 'abort') this.say(L.hideEmbarrassed);
    else if (found) this.say(L.hideFound);
    else {
      this.say(L.hideNotFound);
      this.jumpT = 0.32;
      this.setExpr('LAUGH');
    }
    this.setMood(found ? 'EMBARRASSED' : 'PROUD');
    this.hideResult = null;
  }

  /* ---------------- poke ---------------- */

  clickPipo() {
    if (this.hiding) { this.popHide('found'); this.rig.startSearch(this.pipo.root.position, { close: true }); return; }
    if (this.sleepState === 'asleep' || this.sleepState === 'lying') {
      if (this.sleepState === 'asleep') { this.say(L.ignoreSleep); this.pipo.face.lookAt(0, -0.4); }
      else this.wake('click');
      return;
    }
    if (this.sleepState) { this.wake('click'); return; }
    this.pokeT = 22;
    this.pokeN++;
    this.jumpT = 0.32;
    this.pipo.energy = Math.max(0.05, this.pipo.energy - 0.02);
    this.clearSeq();
    this.stopNav();
    const n = this.pokeN;
    let line = null;
    if (n >= 9) { line = pick(L.pokeMax); this.setExpr('CLOSED'); this.setMood('GRUMPY'); }
    else if (n >= 6) { line = pick(L.pokeMany); this.setExpr('ANNOYED'); this.setMood('GRUMPY'); }
    else if (n >= 4) { line = pick(L.pokeMid); this.setExpr('ANNOYED'); this.setMood('BORED'); }
    else if (n === 1 && Math.random() < 0.5) line = pick(L.pokeHi);
    else if (n > 1) line = pick(L.pokeHi);
    if (Math.random() < 0.35 && n < 4) line = Math.random() < 0.5 ? pick(L.pokeHead) : pick(L.pokeFeet);
    if (line) this.qLine('pipo', line);
    if (n < 4) { this.setMood('HAPPY'); this.setExpr(n === 1 ? 'SURPRISED' : 'HAPPY'); }
    this.setExprHold = 1.2;
  }

  /* ---------------- ball ---------------- */

  kickBall() {
    const b = this.world.ball, v = this.world.ballVel;
    const p = this.pipo.root.position;
    const tx = 150 - p.x, tz = 70 - p.z;
    const d = Math.hypot(tx, tz) || 1;
    const sp = rnd(34, 48);
    v.set((tx / d) * sp, 0, (tz / d) * sp);
    this.jumpT = 0.3;
  }
  nibPlay(action) {
    if (action === 'toys' || action === 'box') {
      this.nibState.mode = 'play';
      this.nibState.timer = rnd(6, 10);
      this.sayNib(L.nibHi);
    }
  }

  /* ---------------- petting Nib ---------------- */

  clickNib() {
    this.petT = 2.6;
    this.sayNib(L.nibHi);
    if (Math.random() < 0.4) this.say(L.reply);
  }
  clickDouble() {
    this.say(L.mirrorOdd);
    this.setMood('NERVOUS');
    this.setExpr('SURPRISED');
  }

  /* ---------------- object clicks ---------------- */

  walkTo(x, z) {
    if (this.sleepState) return;
    if (this.hiding) this.popHide('abort');
    this.clearSeq();
    this.goto(x, z, 17);
    this.idleT = rnd(5, 10);
  }
  toggleSleep() {
    if (this.sleepState) this.wake('click');
    else if (!this.hiding) this.startSleep();
  }
  actOn(action) {
    const rec = this.world.interactables.find(r => r.action === action);
    if (rec && rec.stand) this.rig.startSearch({ x: rec.stand.x, y: 26, z: rec.stand.z }, { close: false });
    if (action === 'sleep') { this.toggleSleep(); return; }
    if (this.sleepState) return;
    if (this.hiding) this.popHide('abort');
    const key = ACTION_ACT[action];
    if (!key || !ACTS[key]) return;
    this.clearSeq();
    this.stopNav();
    this.startSeq(key, ACTS[key].steps(this));
  }
  onWeather(w) {
    if (this.sleepState === 'asleep' || this.hiding) return;
    if (Math.random() < 0.6) this.say(L.weather[w] || L.weather.clear);
  }

  /* ---------------- sleep ---------------- */

  startSleep() {
    if (this.sleepState) return;
    if (this.hiding) this.popHide('abort');
    this.clearSeq();
    this.stopNav();
    this.clearFlags();
    this.sleepState = 'toBed';
    this.sleepT = 0;
    this.climbY = 0;
    this.setMood('SLEEPY');
    this.setExpr('SLEEPY');
    this.say(L.lieDown);
    this.goto(40.5, 44, 17, Math.PI);
  }
  wake(reason) {
    if (!this.sleepState || this.sleepState === 'waking') return;
    const R = this.pipo.root;
    this.wakeFrom = { lie: this.lieK, y: R.position.y, rot: R.rotation.y };
    this.wakeReason = reason || 'click';
    this.sleepState = 'waking';
    this.sleepT = 0;
    this.pipoOpts.sleepy = false;
    this.stopNav();
  }
  sleepTick(dt, t) {
    const st = this.sleepState;
    if (!st) return;
    const R = this.pipo.root;
    this.sleepT += dt;
    if (st === 'toBed') {
      if (!this.nav) { this.sleepState = 'climb'; this.sleepT = 0; this.goto(40.5, 19, 15, Math.PI); }
    } else if (st === 'climb') {
      const k = Math.min(1, this.sleepT / 0.9);
      this.climbY = k;
      R.position.y = 17.2 * k;
      if (k >= 1 && !this.nav) { this.sleepState = 'lying'; this.sleepT = 0; }
    } else if (st === 'lying') {
      const k = Math.min(1, this.sleepT / 1.3);
      this.lieK = k;
      R.position.y = lerp(17.2, 13.24, k);
      R.rotation.y = (Math.PI / 2) * k;
      R.rotation.x = 0;
      this.pose.yaw = lerp(Math.PI, 0, k);
      if (k >= 1) {
        this.sleepState = 'asleep';
        this.sleepT = 0;
        this.setExpr('CLOSED');
        this.setMood('SLEEPY');
        this.mumbleT = rnd(14, 26);
      }
    } else if (st === 'asleep') {
      this.pipo.energy = Math.min(1, this.pipo.energy + dt * 0.12);
      this.mumbleT -= dt;
      if (this.mumbleT <= 0) {
        this.mumbleT = rnd(14, 26);
        this.say(L.sleepMumble);
      }
    } else if (st === 'waking') {
      const f = this.wakeFrom;
      const k = Math.min(1, this.sleepT / 1.7);
      if (k < 0.5) {
        const u = k / 0.5;
        this.lieK = f.lie * (1 - u);
        R.rotation.y = f.rot * (1 - u);
        R.rotation.x = 0;
        if (f.lie > 0.05) { R.position.y = lerp(f.y, 17.2, u); this.pose.yaw = 0; }
      } else {
        const u = (k - 0.5) / 0.5;
        this.lieK = 0;
        R.rotation.y = 0;
        if (f.lie > 0.05) {
          if (!this.nav) this.goto(R.position.x, 48, 30, 0);
          R.position.y = this.nav ? lerp(17.2, 0, u) : 0;
        } else {
          R.position.y = lerp(f.y, 0, u);
        }
      }
      if (k >= 1) {
        R.position.y = 0;
        R.rotation.y = 0;
        R.rotation.x = 0;
        this.lieK = 0;
        this.climbY = 0;
        this.sleepState = null;
        this.wakeFrom = null;
        this.setMood('CURIOUS');
        this.expr = null;
        if (this.wakeReason !== 'find' && Math.random() < 0.75) {
          this.say(this.wakeReason === 'morning' ? L.morning : L.woken);
        }
        this.wakeReason = null;
        if (this.pendingFind) {
          this.pendingFind = false;
          if (!this.seq && !this.nav) this.startHide();
        }
      }
    }
  }

  /* ---------------- mirror ---------------- */

  mirrorSteps() {
    const odd = Math.random() < 0.34;
    const steps = [
      { k: 'go', to: { x: 261, z: 34, face: Math.PI } },
      { k: 'wait', t: 0.4 },
    ];
    if (odd) {
      steps.push(
        { k: 'fn', fn: b => {
          b.world.mirrorMystery = true;
          b.mirrorOddUntil = b.time + 3.4;
          b.setMood('NERVOUS');
          b.setExpr('SURPRISED');
          b.nibState.tellWant = true;
          b.nibState.toldOnce = false;
          b.nibState.mode = 'tell';
          b.nibState.timer = rnd(10, 16);
          b.nibState.naping = false;
        } },
        { k: 'go', to: { x: 246, z: 38, face: Math.PI } },
        { k: 'say', pool: L.mirrorOdd },
        { k: 'wait', t: 1.6 },
        { k: 'fn', fn: b => b.setMood('CURIOUS') },
      );
    } else {
      steps.push(
        { k: 'say', pool: L.vain },
        { k: 'fn', fn: b => { b.pipo.face.lockLook = true; b.pipo.face.lookAt(-0.75, 0.12); } },
        { k: 'do', key: 'mirrorIdle' },
        { k: 'wait', t: rnd(2.6, 4.2) },
        { k: 'fn', fn: b => { b.pipo.face.lockLook = false; b.pipo.face.lookAt(0, 0); } },
      );
    }
    return steps;
  }
  mirrorTick(dt, t) {
    const dbl = this.dbl, w = this.world;
    if (w.mirrorMystery && this.time > this.mirrorOddUntil) w.mirrorMystery = false;
    const pp = this.pipo.root.position;
    const show = Math.abs(pp.x - 274) < 26 && Math.abs(pp.z - 34) < 42 && !this.sleepState && !this.hiding;
    dbl.root.visible = show;
    if (!show) { dbl.update(dt, t, { sleepy: false }); return; }
    const sp = this.pipo.pose, dw = dbl.pose;
    for (const k of ['bob', 'squash', 'stretch', 'lean', 'headTilt', 'headBob', 'armL', 'armR',
      'legL', 'legR', 'spread', 'sit', 'lie', 'liePitch', 'crouch', 'cross', 'cover', 'hold', 'jump', 'shake', 'swing', 'swingAmt']) {
      dw[k] = sp[k];
    }
    if (w.mirrorMystery) {
      dbl.root.position.x = 274;
      dw.yaw = 0;
      dw.headYaw = 0;
      dw.armR = 135 + Math.sin(t * 7) * 32;
      dw.armL = 8;
      dbl.face.lockLook = true;
      dbl.face.lookAt(0, 0.45);
    } else {
      dbl.root.position.x = clamp(274 + (274 - pp.x) * 0.15, 272.6, 275.4);
      dw.yaw = Math.PI - sp.yaw;
      dw.headYaw = -sp.headYaw;
      dbl.face.lockLook = true;
      dbl.face.lookAt(-this.pipo.face.cur.lookX, this.pipo.face.cur.lookY);
    }
    const want = this.expr || DEFAULT_EXPR[this.mood] || null;
    const ek = want + '|' + this.mood;
    if (ek !== dbl._ek) { dbl._ek = ek; dbl.face.setExpr(want, MOOD_GLOW[this.mood]); }
    dbl.update(dt, t, { sleepy: false });
  }

  /* ---------------- Nib ---------------- */

  nibSelect(night) {
    const ns = this.nibState;
    ns.naping = false;
    ns.restY = 0;
    const wantNap = night || this.sleepState === 'asleep';
    if (wantNap) {
      ns.mode = 'nap';
      ns.timer = rnd(30, 60);
      ns.tx = 272; ns.tz = 30; ns.face = Math.PI;
      return;
    }
    if (ns.tellWant && !ns.toldOnce) {
      ns.mode = 'tell';
      ns.timer = rnd(10, 16);
      return;
    }
    const r = Math.random();
    if (r < 0.4) { ns.mode = 'follow'; ns.timer = rnd(8, 20); }
    else if (r < 0.64) { ns.mode = 'wander'; ns.tx = rnd(30, 270); ns.tz = rnd(46, 130); ns.timer = rnd(6, 14); }
    else if (r < 0.86) { ns.mode = 'play'; ns.timer = rnd(8, 16); }
    else if (r < 0.96) { ns.mode = 'stare'; ns.timer = rnd(6, 12); ns.lookT = 1.2; }
    else { ns.mode = 'mirror'; ns.timer = rnd(6, 12); ns.lookT = 2.5; }
  }
  nibTick(dt, t) {
    const ns = this.nibState, N = this.nib, cfg = this.nibCfg, w = this.world;
    const nr = N.root.position, pp = this.pipo.root.position;
    const night = w.env.hour >= 22 || w.env.hour < 5;
    const wantNap = night || this.sleepState === 'asleep';
    delete cfg.lid;
    delete cfg.tailColor;
    cfg.anim = undefined;
    cfg.earUp = 0;
    this.petT = Math.max(0, this.petT - dt);
    if (ns.mode === null || (ns.timer <= 0 && !(ns.mode === 'nap' && wantNap)) || (wantNap && ns.mode !== 'nap')) {
      this.nibSelect(night);
    }
    ns.timer -= dt;
    let face = ns.face, speed = 0, tx = ns.tx, tz = ns.tz;
    if (ns.mode === 'follow') {
      const d = dist2(nr.x, nr.z, pp.x, pp.z);
      tx = pp.x + 7; tz = pp.z + 5;
      if (d > 26) speed = d > 55 ? 34 : 13;
      face = Math.atan2(pp.x - nr.x, pp.z - nr.z);
      ns.face = face;
    } else if (ns.mode === 'wander') {
      if (dist2(nr.x, nr.z, ns.tx, ns.tz) < 4) this.nibSelect(night);
      speed = 13;
      tx = ns.tx; tz = ns.tz;
      face = Math.atan2(tx - nr.x, tz - nr.z);
    } else if (ns.mode === 'play') {
      const b = w.ball;
      const bd = dist2(nr.x, nr.z, b.position.x, b.position.z);
      if (bd < 7) {
        const v = w.ballVel;
        v.x += rnd(-16, 16); v.z += rnd(-16, 16);
        if (Math.hypot(v.x, v.z) < 24) { v.x = rnd(-26, 26); v.z = rnd(-26, 26); }
        cfg.tailColor = 0xffb347;
        ns.timer = Math.min(ns.timer, 1.4);
      } else {
        tx = b.position.x; tz = b.position.z; speed = 24;
      }
      face = Math.atan2(b.position.x - nr.x, b.position.z - nr.z);
      ns.face = face;
    } else if (ns.mode === 'stare') {
      const c = this.camPos();
      face = Math.atan2(c.x - nr.x, c.z - nr.z);
      ns.face = face;
      cfg.anim = 'STARE';
      ns.lookT -= dt;
      if (ns.lookT <= 0) { ns.lookT = rnd(4.5, 7); this.sayNib(L.nibDots); }
    } else if (ns.mode === 'mirror') {
      tx = 264; tz = 34; speed = 14;
      face = Math.PI;
      ns.face = face;
      ns.lookT -= dt;
      if (ns.lookT <= 0) { ns.lookT = rnd(5.5, 8); this.sayNib(L.nibMirror); }
    } else if (ns.mode === 'nap') {
      if (!ns.naping) {
        speed = 14;
        face = Math.PI;
        ns.face = face;
        if (dist2(nr.x, nr.z, 272, 30) < 4.5) { ns.naping = true; ns.restY = 0; }
      } else {
        cfg.anim = 'SLEEP';
        cfg.lid = 1;
        ns.restY = Math.min(8, ns.restY + dt * 5);
      }
    } else if (ns.mode === 'tell') {
      const d = dist2(nr.x, nr.z, pp.x, pp.z);
      tx = pp.x + 9; tz = pp.z + 5;
      speed = d > 16 ? 36 : 12;
      face = Math.atan2(pp.x - nr.x, pp.z - nr.z);
      ns.face = face;
      if (d < 15 && !ns.toldOnce) {
        ns.toldOnce = true;
        ns.tellWant = false;
        this.sayNib(L.nibTells);
        this.setMood('NERVOUS');
        this.setExpr('NERVOUS');
        ns.timer = Math.min(ns.timer, 6);
      }
    }
    if (speed > 0 && tx !== undefined && tz !== undefined) {
      const d = dist2(nr.x, nr.z, tx, tz);
      if (d > 2.2) {
        const s = Math.min(speed * dt, d);
        nr.x = clamp(nr.x + ((tx - nr.x) / d) * s, 14, 282);
        nr.z = clamp(nr.z + ((tz - nr.z) / d) * s, 26, 134);
        cfg.anim = speed > 28 ? 'RUN' : 'WALK';
        face = Math.atan2(tx - nr.x, tz - nr.z);
      }
    }
    if (face !== undefined) N.state.facing = face;
    if (this.petT > 0) { cfg.anim = 'WAG'; cfg.earUp = 1; }
    const targetY = (ns.mode === 'nap' && ns.naping) ? ns.restY : 0;
    nr.y += (targetY - nr.y) * Math.min(1, dt * 6);
    if (nr.y < 0.001) nr.y = 0;
    N.update(dt, t, cfg);
  }

  /* ---------------- idle & timers ---------------- */

  idleTick(dt) {
    this.idleT -= dt;
    if (this.idleT > 0) return;
    if (this.seq || this.nav || this.sleepState || this.hiding) { this.idleT = 2.5; return; }
    if (this.lineQ.length || this.qWait > 0) { this.idleT = 3; return; }
    this.idleT = rnd(7, 15);
    const h = this.world.env.hour;
    const night = h >= 22 || h < 5;
    let table = IDLE_TABLE;
    if (night || this.pipo.energy < 0.32) table = IDLE_TABLE.concat([['sleep', night ? 6 : 3.5]]);
    let total = 0;
    for (const e of table) total += e[1] * (e[0] === this.lastKey ? 0.25 : 1);
    let r = Math.random() * total, key = table[0][0];
    for (const e of table) {
      r -= e[1] * (e[0] === this.lastKey ? 0.25 : 1);
      if (r <= 0) { key = e[0]; break; }
    }
    this.lastKey = key;
    if (key === 'sleep') { this.startSleep(); return; }
    const A = ACTS[key];
    if (A) this.startSeq(key, A.steps(this));
  }
  timers(dt) {
    if (this.pokeT > 0) { this.pokeT -= dt; if (this.pokeT <= 0) this.pokeN = 0; }
    if (this.setExprHold > 0) this.setExprHold -= dt;
    if (this.qWait > 0) {
      this.qWait -= dt;
    } else if (this.lineQ.length) {
      const l = this.lineQ.shift();
      this.bubble(l.who, l.text);
      this.qWait = 1.3 + l.text.length * 0.035;
    }
    const h = this.world.env.hour;
    const night = h >= 22 || h < 5;
    if (night && !this.wasNight) {
      this.wasNight = true;
      if (!this.sleepState && !this.hiding) {
        this.say(L.lateNight);
        this.startSleep();
      }
    } else if (!night) this.wasNight = false;
    const morning = h >= 6 && h < 9;
    if (morning && !this.wasMorning) {
      this.wasMorning = true;
      if (this.sleepState) this.wake('morning');
      else if (!this.hiding && Math.random() < 0.6) this.say(L.morning);
    } else if (!morning) this.wasMorning = false;
  }

  /* ---------------- pose & main loop ---------------- */

  poseTick(dt, t) {
    const P = this.pipo.pose;
    const S = this.sleepState;
    this.moving = !!this.nav;
    for (const k in BASE) P[k] = BASE[k];
    const sleepLie = this.lieK > 0.02 && S;
    if (sleepLie) {
      P.bob = Math.sin(t * 1.15) * 0.6;
      P.headBob = Math.sin(t * 1.05) * 1.5;
    } else if (this.moving) {
      const ph = this.walkPhase;
      P.swing = Math.sin(ph) * 0.62;
      P.swingAmt = 1;
      P.legL = Math.max(0, Math.sin(ph)) * 10;
      P.legR = Math.max(0, -Math.sin(ph)) * 10;
      P.bob = Math.abs(Math.sin(ph)) * 1.3;
      P.lean = 5;
      P.headBob = -2;
      P.headYaw = 0;
      P.armL = 7; P.armR = 7;
    } else if (S) {
      P.bob = Math.sin(t * 1.6) * 0.4;
    } else if (this.hiding) {
      P.sit = 1; P.cross = 1; P.cover = 1;
      P.headBob = 13 + Math.sin(t * 2.2) * 2;
      P.armL = 24; P.armR = 24;
    } else {
      P.bob = Math.sin(t * 1.6) * 0.5;
      P.headBob = Math.sin(t * 0.55) * 4;
      P.headTilt = Math.sin(t * 0.37) * 5;
      P.headYaw = Math.sin(t * 0.21) * 7;
      P.armL = 8 + Math.sin(t * 1.2) * 3;
      P.armR = 8 + Math.cos(t * 1.07) * 3;
    }
    if (this.actPose && this.actPose.pose) Object.assign(P, this.actPose.pose(this.actT));
    if (this.jumpT > 0) {
      this.jumpT = Math.max(0, this.jumpT - dt);
      P.jump = Math.sin(Math.PI * (1 - this.jumpT / 0.32)) * 13;
    }
    if (S) {
      P.lie = this.lieK;
      P.liePitch = 0.528 * this.lieK;
      if (this.lieK > 0.02) {
        P.legL = 0; P.legR = 0;
        P.armL = 6; P.armR = 6;
        P.spread = 0;
      }
    }
  }
  update(dt, t) {
    this.time = t;
    this.timers(dt);
    this.sleepTick(dt, t);
    this.stepTick(dt);
    this.navTick(dt);
    this.idleTick(dt);
    this.poseTick(dt, t);
    this.mirrorTick(dt, t);
    this.nibTick(dt, t);
    const charging = !!this.world.charging;
    const asleep = this.sleepState === 'asleep';
    if (charging || asleep) {
      this.pipo.energy += (1 - this.pipo.energy) * Math.min(1, dt * 0.45);
    } else {
      this.pipo.energy = Math.max(0.02, this.pipo.energy - dt * 0.0018);
    }
    this.pipoOpts.sleepy = asleep || this.pipo.energy < 0.22;
    this.moodT = Math.max(0, this.moodT - dt);
    const want = this.expr || DEFAULT_EXPR[this.mood] || null;
    const ek = want + '|' + this.mood;
    if (ek !== this._exprKey) {
      this._exprKey = ek;
      this.pipo.face.setExpr(want, MOOD_GLOW[this.mood]);
    }
  }
}

const DEFAULT_EXPR = {
  HAPPY: 'HAPPY', SLEEPY: 'SLEEPY', GRUMPY: 'ANNOYED', BORED: 'BORED',
  CURIOUS: 'CURIOUS', NERVOUS: 'NERVOUS', EMBARRASSED: 'EMBARRASSED',
  PROUD: 'PROUD', MISCHIEVOUS: 'MISCHIEF', PLAYFUL: 'HAPPY',
  RELAXED: 'CONTENT', EXCITED: 'EXCITED', FOCUSED: 'FOCUSED',
  THOUGHTFUL: 'CURIOUS', CALM: 'CONTENT', WORRIED: 'WORRIED',
};

const ACTION_ACT = {
  watch: 'tv', computer: 'computer', build: 'build', arcade: 'arcade',
  fridge: 'fridge', charge: 'charge', door: 'door', drawings: 'drawings',
  inspect: 'plant', mirror: 'mirror', kick: 'kick', window: 'window',
};
