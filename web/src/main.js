import * as THREE from 'three';
import { World } from './scene.js';
import { CameraRig } from './camera.js';
import { Pipo, Nib } from './pipo.js';
import { Brain } from './brain.js';

const $ = id => document.getElementById(id);
const canvas = $('world');
const urlQ = new URLSearchParams(location.search).get('q');
const coarse = matchMedia('(pointer: coarse)').matches;
const low = urlQ === 'low' || (coarse && (navigator.hardwareConcurrency || 4) <= 4);

const renderer = new THREE.WebGLRenderer({ canvas, antialias: !low, powerPreference: 'high-performance' });
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
const pr = Math.min(window.devicePixelRatio || 1, low ? 1.5 : 2);
renderer.setPixelRatio(pr);

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x0d111c);

const camera = new THREE.PerspectiveCamera(40, innerWidth / innerHeight, 1, 1200);

const world = new World(scene, { low, shadow: low ? 1024 : 2048 });
const rig = new CameraRig(camera, canvas);
const pipo = new Pipo();
const nib = new Nib();
scene.add(pipo.root);
scene.add(nib.root);

const niche = new THREE.PointLight(0xC9DBFF, 150, 110, 2);
niche.position.set(274, 27, -14);
scene.add(niche);

const bubLayer = $('bubbles');
const V = new THREE.Vector3();
const bubbleItems = [];

function say(who, text) {
  if (who === 'pipo') pipo.sayFor(Math.min(4.5, 1.5 + text.length * 0.055));
  const el = document.createElement('div');
  el.className = 'bubble' + (who === 'nib' ? ' nib' : '');
  const w = document.createElement('span');
  w.className = 'who';
  w.textContent = who === 'nib' ? 'Nib' : 'Pipo';
  const tx = document.createElement('span');
  tx.textContent = text;
  el.append(w, tx);
  bubLayer.appendChild(el);
  bubbleItems.push({ who, el, born: performance.now() });
  while (bubbleItems.length > 3) removeBubble(bubbleItems[0]);
}
function removeBubble(it) {
  const i = bubbleItems.indexOf(it);
  if (i < 0) return;
  bubbleItems.splice(i, 1);
  it.el.classList.add('out');
  setTimeout(() => it.el.remove(), 520);
}
function updateBubbles() {
  const now = performance.now();
  const w = innerWidth, h = innerHeight;
  for (const it of [...bubbleItems]) {
    if (now - it.born > 4800) { removeBubble(it); continue; }
    if (it.who === 'nib') V.set(nib.root.position.x, nib.root.position.y + 17, nib.root.position.z);
    else { pipo.headWorld(V); V.y += 9; }
    V.project(camera);
    if (V.z < 1) {
      it.el.style.opacity = '';
      it.el.style.left = ((V.x * 0.5 + 0.5) * w) + 'px';
      it.el.style.top = ((-V.y * 0.5 + 0.5) * h) + 'px';
    } else {
      it.el.style.opacity = '0';
    }
  }
}

const brain = new Brain({
  world, scene, rig, pipo, nib,
  say,
  search: (pos, o) => rig.startSearch(pos, o),
  searching: () => !!rig.search,
  camPos: () => camera.position,
});
world.onWeather = w => brain.onWeather(w);
window.P3D = { brain, pipo, nib, rig, world, camera, renderer };

const ray = new THREE.Raycaster();
const ndc = new THREE.Vector2();
let downX = 0, downY = 0, downT = 0, dragged = false;

function ownerOf(obj) {
  let o = obj;
  while (o) {
    if (o === pipo.root) return 'pipo';
    if (o === nib.root) return 'nib';
    if (o === brain.dbl.root) return 'double';
    if (o.userData && o.userData.oid) return o.userData.oid;
    o = o.parent;
  }
  return null;
}

function tap(e) {
  ndc.set((e.clientX / innerWidth) * 2 - 1, -(e.clientY / innerHeight) * 2 + 1);
  ray.setFromCamera(ndc, camera);
  const targets = [pipo.root, nib.root, brain.dbl.root, ...world.clickMeshes];
  const hits = ray.intersectObjects(targets, true);
  if (hits.length) {
    const id = ownerOf(hits[0].object);
    if (id === 'pipo') { brain.clickPipo(); return; }
    if (id === 'nib') { brain.clickNib(); return; }
    if (id === 'double') { brain.clickDouble(); return; }
    if (id) {
      const rec = world.interactables.find(r => r.id === id);
      if (!rec) return;
      if (rec.nibOnly) { brain.nibPlay(rec.action); return; }
      if (rec.click) rec.click();
      brain.actOn(rec.action);
      return;
    }
  }
  const hitFloor = ray.intersectObject(world.floor, false);
  if (hitFloor.length) {
    const p = hitFloor[0].point;
    if (p.x >= 14 && p.x <= 282 && p.z >= 26 && p.z <= 134) brain.walkTo(p.x, p.z);
  }
}

canvas.addEventListener('pointerdown', e => {
  downX = e.clientX; downY = e.clientY; downT = performance.now(); dragged = false;
  brain.noteCameraTouch();
});
canvas.addEventListener('pointermove', e => {
  if (Math.hypot(e.clientX - downX, e.clientY - downY) > 7) dragged = true;
});
canvas.addEventListener('pointerup', e => {
  if (dragged || performance.now() - downT > 600) return;
  tap(e);
});
canvas.addEventListener('wheel', () => brain.noteCameraTouch(), { passive: true });

const hint = $('hint');
let entered = false;
$('btnEnter').addEventListener('click', () => {
  $('splash').classList.add('gone');
  hint.classList.remove('hidden');
  setTimeout(() => hint.classList.add('hidden'), 7000);
  entered = true;
  brain.firstWake();
});
$('btnFind').addEventListener('click', () => brain.find());
$('btnFollow').addEventListener('click', e => {
  const on = !rig.follow;
  rig.setFollow(on);
  e.currentTarget.classList.toggle('on', on);
});
$('btnReset').addEventListener('click', () => rig.reset());

function resize() {
  camera.aspect = innerWidth / innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(innerWidth, innerHeight);
}
addEventListener('resize', resize);
resize();

let fpsT = 0, fpsN = 0, grade = 0;
let loaded = false;
const clock = new THREE.Clock();

renderer.setAnimationLoop(() => {
  const dt = Math.min(clock.getDelta(), 0.05);
  const t = clock.elapsedTime;
  world.update(dt, t);
  if (entered) brain.update(dt, t);
  rig.followPos.copy(pipo.root.position);
  rig.update(dt, t);
  pipo.update(dt, t, brain.pipoOpts);
  updateBubbles();
  renderer.render(scene, camera);
  if (!loaded) { loaded = true; $('loading').classList.add('gone'); }
  fpsN++; fpsT += dt;
  if (fpsT >= 3) {
    const fps = fpsN / fpsT;
    if (fps < 34) {
      if (grade === 0) { renderer.setPixelRatio(1); grade = 1; }
      else if (grade === 1) { world.sun.castShadow = false; grade = 2; }
    }
    fpsN = 0; fpsT = 0;
  }
});
