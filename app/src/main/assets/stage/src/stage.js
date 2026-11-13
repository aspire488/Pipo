import * as THREE from 'three';
import { World } from './scene.js';
import { CameraRig } from './camera.js';
import { Nib, MOOD_GLOW } from './pipo.js';
import { loadPipoModel, PipoModel, suitKey, SUITS } from './character.js';
import { StageFX, STATES } from './fx.js';

/*
 * Pipo's 3D stage. It renders; it doesn't decide. A host owns the truth and drives it:
 *  - on Android: the app's engine (HomeViewModel) through window.PipoAndroid
 *  - in a browser: preview-host.js (local preview only)
 * Host -> stage: PipoStage.apply(partialState)
 * Stage -> host: host.send(text), host.action(id), host.tap(target), host.ready()
 */

const $ = id => document.getElementById(id);
const canvas = $('world');
const coarse = matchMedia('(pointer: coarse)').matches;
const params = new URLSearchParams(location.search);
const low = params.get('q') === 'low' || (coarse && (navigator.hardwareConcurrency || 4) <= 4) || !!window.PipoAndroid?.lowEnd?.();

const LANE_Z = 50;   // where he walks in the room (in front of the furniture)

/* ---------------- host ---------------- */
const android = window.PipoAndroid;
let host = android ? {
  send: t => android.send(t), action: a => android.action(a), tap: t => android.tap(t), ready: () => android.ready(),
} : null;   // filled by the preview host below

/* ---------------- renderer ---------------- */
let renderer;
try {
  renderer = new THREE.WebGLRenderer({ canvas, antialias: !low, powerPreference: 'high-performance' });
} catch (e) {
  fatal('3D is not available on this device.', e);
}
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.12;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
renderer.setPixelRatio(Math.min(devicePixelRatio || 1, low ? 1.25 : 2));

const room = new THREE.Scene();
room.background = new THREE.Color(0x0d111c);
const camera = new THREE.PerspectiveCamera(40, innerWidth / innerHeight, 1, 2400);
const world = new World(room, { low, shadow: low ? 1024 : 2048 });
world.env.speed = 0;                     // the host's clock, not a fast demo day
const rig = new CameraRig(camera, canvas);
rig.followDist = innerHeight > innerWidth ? 185 : 140;
rig.followLift = 25;               // frame his face, not the top of his head
rig.goalAz = rig.az = 0.2; rig.goalEl = rig.el = 0.2;   // eye-level three-quarter view: welcoming, not looming
rig.setFollow(true);

let forest = null;                       // built on first visit
let active = room;

const nib = new Nib();
room.add(nib.root);
nib.root.position.set(120, 0, LANE_Z + 14);

const fx = new StageFX();
let pipo = null;                         // PipoModel once loaded
let charLights = null;              // key / fill / rim that travel with him

/* ---------------- state ---------------- */
const S = {
  x: 148, z: LANE_Z, lift: 0, pose: null, expr: 'CONTENT', mood: 'CURIOUS', look: [0, 0], talking: false, sleepy: false,
  suit: { mk: 0, cardboard: false, owned: 0, ownedCardboard: false, flying: false },
  nib: { x: 120, anim: 'IDLE', visible: true, facing: 0 },
  away: false, place: null,
  ai: { state: 'idle', panel: null },
  hud: { status: '', choices: [], asleep: false },
  env: null,
};
let lastSayId = null, lastToastId = null, lastExpr = null, lastMood = null;

function apply(patch) {
  for (const [k, v] of Object.entries(patch || {})) {
    if (v && typeof v === 'object' && !Array.isArray(v) && S[k] && typeof S[k] === 'object' && k !== 'pose') S[k] = { ...S[k], ...v };
    else S[k] = v;
  }
  if (patch.say && patch.say.id !== lastSayId) { lastSayId = patch.say.id; if (patch.say.text) say(patch.say.who || 'pipo', patch.say.text); }
  if (patch.toast && patch.toast.id !== lastToastId) { lastToastId = patch.toast.id; toast(patch.toast.text, patch.toast.kind); }
  if (patch.env) syncEnv(patch.env);
  if (patch.hud) syncHud();
  if (patch.ai) syncAi();
  if (patch.suit) syncSuit();
  if ('place' in patch || 'away' in patch) syncPlace();
}

function syncEnv(e) {
  if (typeof e.hour === 'number') world.env.hour = e.hour;
  const w = { clear: 'clear', wind: 'clear', cloudy: 'cloudy', fog: 'cloudy', rain: 'rain', storm: 'rain' }[String(e.weather || '').toLowerCase()];
  if (w && w !== world.env.weather) { world.env.setWeather(w); world.env.weatherT = 1e9; }
}

function syncSuit() {
  if (!pipo) return;
  const s = S.suit;
  const key = suitKey(s.mk, s.cardboard);
  const changed = pipo.equip(key);
  pipo.flying = !!s.flying;
  // the dock shows the best suit he owns, whenever he isn't wearing it
  const ownedKey = suitKey(s.owned || (s.ownedCardboard ? 1 : 0), !s.owned && s.ownedCardboard);
  if (fx.dockSuit && ownedKey) fx.dockSuit.equip(ownedKey, true);
  fx.placeDockSuit(null, !!ownedKey && !key);
  const btn = $('qaSuit');
  btn.setAttribute('aria-pressed', String(!!key));
  btn.querySelector('.lb').textContent = key ? `Suit off (${SUITS[key].label})` : 'Suit on';
  if (changed && key) burst(SUITS[key].core);
}

function syncAi() {
  const st = S.ai.state in STATES ? S.ai.state : 'idle';
  fx.set(st, S.ai.panel);
  const pill = $('statePill');
  const text = S.ai.panel?.status === 'running' ? `${STATES[st].label || 'Working'}: ${S.ai.panel.title}` : STATES[st].label;
  pill.hidden = !text;
  pill.dataset.s = st;
  pill.textContent = text || '';
  $('btnMic').classList.toggle('on', st === 'listening');
}

function syncHud() {
  const box = $('choices');
  const ch = S.hud.choices || [];
  box.hidden = !ch.length;
  box.replaceChildren(...ch.map((label, i) => {
    const b = document.createElement('button');
    b.textContent = label;
    b.onclick = () => { box.hidden = true; host?.action('choice:' + i); };
    return b;
  }));
  $('qaSleep').querySelector('.lb').textContent = S.hud.asleep ? 'Wake up' : 'Sleep';
}

function syncPlace() {
  const want = S.away && S.place === 'forest' ? 'forest' : 'room';
  if (want === 'forest' && !forest) {
    // lazy: only phones that go there pay for it
    import('./forest.js').then(m => { forest = new m.ForestHills({ low }); syncPlace(); });
    return;
  }
  const next = want === 'forest' ? forest.scene : room;
  if (next === active || !pipo) return;
  next.add(pipo.root); next.add(nib.root); next.add(fx.halo); next.add(fx.holo);
  active = next;
  rig.followPos.copy(pipo.root.position);
  rig.reset(); rig.goalEl = next === room ? 0.2 : 0.16; rig.goalAz = 0.2; rig.setFollow(true);
}

/* ---------------- bubbles + toasts ---------------- */
const bubbleItems = [];
function say(who, text) {
  if (who === 'pipo') pipo?.sayFor(Math.min(4.5, 1.5 + text.length * 0.055));
  const el = document.createElement('div');
  el.className = 'bubble' + (who === 'nib' ? ' nib' : '');
  const w = document.createElement('span'); w.className = 'who'; w.textContent = who === 'nib' ? 'Nib' : 'Pipo';
  const tx = document.createElement('span'); tx.textContent = text;
  el.append(w, tx);
  $('bubbles').appendChild(el);
  bubbleItems.push({ who, el, born: performance.now(), life: 3500 + text.length * 45 });
  while (bubbleItems.length > 2) dropBubble(bubbleItems[0]);
}
function dropBubble(it) {
  const i = bubbleItems.indexOf(it); if (i < 0) return;
  bubbleItems.splice(i, 1); it.el.classList.add('out'); setTimeout(() => it.el.remove(), 450);
}
const V = new THREE.Vector3();
function updateBubbles() {
  const now = performance.now();
  for (const it of [...bubbleItems]) if (now - it.born > it.life) dropBubble(it);
  // newest sits on the speaker; older ones stack above it by their real height
  let stack = 0;
  for (let i = bubbleItems.length - 1; i >= 0; i--) {
    const it = bubbleItems[i];
    if (it.who === 'nib') V.set(nib.root.position.x, nib.root.position.y + 18, nib.root.position.z);
    else if (pipo) { pipo.headWorld(V); V.y += 26; } else continue;
    V.project(camera);
    it.el.style.left = Math.min(innerWidth - 90, Math.max(90, (V.x * 0.5 + 0.5) * innerWidth)) + 'px';
    it.el.style.top = Math.max(120 + stack + it.el.offsetHeight, (-V.y * 0.5 + 0.5) * innerHeight - stack) + 'px';
    it.el.style.opacity = V.z < 1 ? '' : '0';
    stack += it.el.offsetHeight + 10;
  }
}
function toast(text, kind = '') {
  const el = document.createElement('div');
  el.className = 'toast ' + kind; el.textContent = text;
  $('toasts').appendChild(el);
  setTimeout(() => { el.classList.add('out'); setTimeout(() => el.remove(), 500); }, 3200);
  while ($('toasts').children.length > 3) $('toasts').firstChild.remove();
}

/* suit-up sparkle */
const sparks = [];
function burst(color) {
  if (!pipo) return;
  const g = new THREE.Mesh(new THREE.TorusGeometry(10, 0.6, 8, 40), new THREE.MeshBasicMaterial({ color, transparent: true, toneMapped: false }));
  g.rotation.x = Math.PI / 2;
  g.position.copy(pipo.root.position).setY(pipo.root.position.y + 2);
  active.add(g); sparks.push({ g, t: 0 });
}

/* ---------------- input ---------------- */
const ray = new THREE.Raycaster(), ndc = new THREE.Vector2();
let down = null;
canvas.addEventListener('pointerdown', e => { down = { x: e.clientX, y: e.clientY, t: performance.now() }; rig.setFollow(false); });
canvas.addEventListener('pointerup', e => {
  if (!down || Math.hypot(e.clientX - down.x, e.clientY - down.y) > 8 || performance.now() - down.t > 600) { down = null; return; }
  down = null;
  ndc.set(e.clientX / innerWidth * 2 - 1, -(e.clientY / innerHeight) * 2 + 1);
  ray.setFromCamera(ndc, camera);
  const owner = o => { for (; o; o = o.parent) { if (pipo && o === pipo.root) return 'pipo'; if (o === nib.root) return 'nib'; if (o.userData?.oid) return 'obj:' + o.userData.oid; } return null; };
  const targets = [pipo?.root, nib.root, ...(active === room ? world.clickMeshes : [])].filter(Boolean);
  const hit = ray.intersectObjects(targets, true)[0];
  const id = hit && owner(hit.object);
  if (id) { host?.tap(id); return; }
  if (active === room) {
    const f = ray.intersectObject(world.floor, false)[0];
    if (f && f.point.x > 14 && f.point.x < 282) host?.tap('floor:' + f.point.x.toFixed(1));
  }
});

$('composer').addEventListener('submit', e => {
  e.preventDefault();
  const t = $('msg').value.trim();
  if (!t) return;
  $('msg').value = '';
  host?.send(t);
});
$('quick').addEventListener('click', e => {
  const b = e.target.closest('.qa'); if (!b) return;
  const act = b.dataset.act;
  if (act === 'camera') { const on = !rig.follow; rig.setFollow(on); b.setAttribute('aria-pressed', String(on)); return; }
  if (act === 'findout') {
    const q = $('msg').value.trim();
    if (!q) { $('msg').placeholder = 'Type what Pipo should find out, then tap Find out'; $('msg').focus(); return; }
    $('msg').value = '';
    host?.action('findout:' + q); return;
  }
  host?.action(act);
});
$('btnMenu').addEventListener('click', () => host?.action('menu'));
$('btnMic').addEventListener('click', () => host?.action('mic'));

/* ---------------- frame ---------------- */
function resize() {
  camera.aspect = innerWidth / innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(innerWidth, innerHeight);
  rig.followDist = innerHeight > innerWidth ? 185 : 140;
}
addEventListener('resize', resize);
resize();

const clock = new THREE.Clock();
let fpsT = 0, fpsN = 0, grade = 0, running = true;
document.addEventListener('visibilitychange', () => { running = !document.hidden; clock.getDelta(); });

function frame() {
  if (!running) return;
  const dt = Math.min(clock.getDelta(), 0.05);
  const t = clock.elapsedTime;
  world.update(dt, t);
  if (forest && active === forest.scene) forest.update(dt, t, world.env.day);

  if (pipo) {
    if (S.pose) Object.assign(pipo.pose, S.pose);
    const inForest = active !== room;
    const px = inForest ? forest.center.x + ((S.x - 148) * 0.25) : S.x;
    const pz = inForest ? forest.center.z : S.z;
    const ground = inForest ? forest.groundAt(px, pz) : 0;
    pipo.root.position.x += (px - pipo.root.position.x) * (1 - Math.exp(-14 * dt));
    pipo.root.position.z = pz;
    pipo.root.position.y = ground + (S.lift || 0);
    pipo.root.visible = !S.away || inForest;
    if (S.expr !== lastExpr || S.mood !== lastMood) {
      lastExpr = S.expr; lastMood = S.mood;
      pipo.face.setExpr(S.expr, MOOD_GLOW[S.mood]);
      pipo.setMood(S.mood);
      $('moodDot').style.background = '#' + new THREE.Color(MOOD_GLOW[S.mood] ?? MOOD_GLOW.CURIOUS).getHexString();
    }
    pipo.face.lookAt(S.look?.[0] || 0, S.look?.[1] || 0);
    pipo.update(dt, t, { sleepy: S.sleepy, talking: S.talking || S.ai.state === 'speaking', glow: S.sleepy ? 0.4 : 1 });
    rig.followPos.copy(pipo.root.position);
    if (charLights.group.parent !== active) active.add(charLights.group);
    {
      const p = pipo.root.position, night = active === room ? world.env.night : 0.3;
      // placed relative to the camera so the face is always lit from the front three-quarter
      const az = rig.az;
      const at = (l, dAz, dist, h) => { l.position.set(p.x + Math.sin(az + dAz) * dist, p.y + h, p.z + Math.cos(az + dAz) * dist); l.target.position.set(p.x, p.y + 20, p.z); };
      at(charLights.key, -0.6, 90, 70);
      at(charLights.fill, 0.9, 100, 30);
      at(charLights.rim, Math.PI - 0.3, 80, 60);
      charLights.key.intensity = 6200 * (0.55 + 0.45 * night);
      charLights.fill.intensity = 2600 * (0.5 + 0.5 * night);
      charLights.rim.intensity = 5200 * (0.4 + 0.6 * night);
    }
    // the face follows what he's actually doing: thinking looks up and away, listening and talking look at you
    const ais = S.ai.state;
    if (ais === 'thinking' || ais === 'working') pipo.face.hold(0.42, -0.55);
    else if (ais === 'listening' || ais === 'speaking') pipo.face.hold(0, 0.05);
    else pipo.face.hold(null);
    pipo.headWorld(V);
    fx.update(dt, t, pipo.root.position, V, camera);
    fx.halo.visible = pipo.root.visible;
  }
  // Nib
  const n = S.nib;
  const inForest = active !== room;
  nib.root.visible = n.visible !== false && (!S.away || (inForest && n.withPipo));
  const nx = inForest ? (pipo?.root.position.x ?? 148) - 18 : n.x;
  const nz = inForest ? (pipo?.root.position.z ?? 60) + 10 : LANE_Z + 14;
  nib.root.position.x += (nx - nib.root.position.x) * (1 - Math.exp(-8 * dt));
  nib.root.position.z = nz;
  nib.root.position.y = inForest ? forest.groundAt(nib.root.position.x, nz) : 0;
  nib.state.facing = n.facing ?? 0;
  nib.update(dt, t, { anim: n.anim || 'IDLE', lookX: 0, lookY: 0 });

  for (let i = sparks.length - 1; i >= 0; i--) {
    const s = sparks[i]; s.t += dt;
    s.g.scale.setScalar(1 + s.t * 3); s.g.position.y += dt * 30; s.g.material.opacity = Math.max(0, 1 - s.t / 0.8);
    if (s.t > 0.8) { s.g.removeFromParent(); s.g.geometry.dispose(); s.g.material.dispose(); sparks.splice(i, 1); }
  }

  rig.update(dt, t);
  updateBubbles();
  renderer.render(active, camera);

  // keep frame pacing: shed pixels, then shadows, before dropping frames
  fpsN++; fpsT += dt;
  if (fpsT >= 3) {
    const fps = fpsN / fpsT;
    if (fps < 40 && grade === 0) { renderer.setPixelRatio(1); grade = 1; }
    else if (fps < 34 && grade === 1) { world.sun.castShadow = false; if (forest) forest.sun.castShadow = false; grade = 2; }
    window.__pipoFps = Math.round(fps);
    fpsN = 0; fpsT = 0;
  }
}

function fatal(msg, err) {
  console.error('[stage]', msg, err);
  const f = $('fatal'); f.hidden = false; f.textContent = msg;
  $('loading').classList.add('gone');
  try { window.PipoAndroid?.action('stage-error:' + String(err?.message || msg).slice(0, 200)); } catch (_) { /* host gone */ }
  throw err || new Error(msg);
}
addEventListener('error', e => { if (!pipo) fatal('The 3D world failed to load.', e.error); });

/* ---------------- boot ---------------- */
window.PipoStage = { apply, get state() { return S; } };
(async () => {
  try {
    const [gltf] = await Promise.all([loadPipoModel(), fx.load(room)]);
    pipo = new PipoModel(gltf);
    pipo.root.position.set(S.x, 0, S.z);
    room.add(pipo.root);
    fx.placeDockSuit(new PipoModel(gltf, { armorOnly: true }), false);
    pipo.setMood(S.mood);
    // character lighting: soft warm key from the camera side, cool fill, a rim to lift him off the background
    const spot = (color, angle) => { const l = new THREE.SpotLight(color, 0, 0, angle, 1, 2); l.target = new THREE.Object3D(); return l; };
    charLights = { key: spot(0xFFEEDD, 0.42), fill: spot(0xCFE2FF, 0.6), rim: spot(0xC8EEFF, 0.38), group: new THREE.Group() };
    charLights.key.castShadow = true;
    charLights.key.shadow.mapSize.set(low ? 512 : 1024, low ? 512 : 1024);
    charLights.key.shadow.radius = 6; charLights.key.shadow.bias = -0.0015;
    for (const k of ['key', 'fill', 'rim']) charLights.group.add(charLights[k], charLights[k].target);
    room.add(charLights.group);
    syncSuit(); syncAi(); syncHud(); syncPlace();
    renderer.setAnimationLoop(frame);
    $('loading').classList.add('gone');
    if (host) host.ready();
    else {
      const m = await import('./preview-host.js');
      host = window.PipoPreview = m.start(apply, () => S);
    }
  } catch (e) { fatal('The 3D world failed to load.', e); }
})();
window.__pipoStageDebug = { world, rig, fx, nib, get pipo() { return pipo; }, get forest() { return forest; }, renderer };
