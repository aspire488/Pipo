import * as THREE from 'three';
import { RoundedBoxGeometry } from '../vendor/RoundedBoxGeometry.js';

const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
const lerp = (a, b, t) => a + (b - a) * t;
const lerpC = (a, b, t) => new THREE.Color(a).lerp(new THREE.Color(b), t);
const rnd = (a, b) => a + Math.random() * (b - a);

const WOOD = 0x7B5A45, WOOD_D = 0x5E4434, INK = 0x2B3342;

const std = (color, rough = .72, metal = .02, extra = {}) =>
  new THREE.MeshStandardMaterial({ color, roughness: rough, metalness: metal, ...extra });

function rbox(w, h, d, r, color, opts = {}) {
  const rr = Math.min(r, w / 2 - .01, h / 2 - .01, d / 2 - .01);
  const g = rr > .02 ? new RoundedBoxGeometry(w, h, d, 2, rr) : new THREE.BoxGeometry(w, h, d);
  const m = new THREE.Mesh(g, opts.mat || std(color, opts.rough ?? .72, opts.metal ?? .02, opts.extra));
  m.castShadow = opts.cast !== false;
  m.receiveShadow = opts.recv !== false;
  return m;
}
function plane(w, h, mat) { const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), mat); return m; }

function canvasTex(w, h, draw) {
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  const x = c.getContext('2d'); draw(x, w, h);
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace;
  return { tex: t, canvas: c, ctx: x, redraw: d => { x.clearRect(0, 0, w, h); d(x, w, h); t.needsUpdate = true; } };
}

/* ================= ENVIRONMENT (time + weather) ================= */

export class Env {
  constructor(world) {
    this.world = world;
    this.hour = 16.6;
    this.speed = 24 / 480;          // full day in 8 minutes
    this.weather = 'clear';
    this.weatherT = rnd(50, 90);
    this.day = 1; this.night = 0;
    this.flash = 0;
  }
  advance(h) { this.hour = (this.hour + h) % 24; }
  setWeather(w) {
    this.weather = w;
    this.weatherT = rnd(55, 110);
    if (this.world.onWeather) this.world.onWeather(w);
  }
  cycleWeather() {
    const order = ['clear', 'cloudy', 'rain'];
    this.setWeather(order[(order.indexOf(this.weather) + 1) % 3]);
  }
  update(dt) {
    this.hour = (this.hour + this.speed * dt) % 24;
    const h = this.hour;
    let day;
    if (h < 5) day = 0;
    else if (h < 8) day = (h - 5) / 3;
    else if (h < 17.5) day = 1;
    else if (h < 20.5) day = 1 - (h - 17.5) / 3;
    else day = 0;
    let d = day;
    if (this.weather === 'cloudy') d *= 0.82;
    if (this.weather === 'rain') d *= 0.62;
    this.day += (d - this.day) * (1 - Math.exp(-2 * dt));
    this.night = 1 - this.day;

    this.weatherT -= dt;
    if (this.weatherT <= 0) {
      const r = Math.random();
      this.setWeather(r < .55 ? 'clear' : r < .8 ? 'cloudy' : 'rain');
    }
  }
  get evening() { const h = this.hour; return h > 16 && h < 20.5 ? clamp((h - 16) / 3, 0, 1) : 0; }
}

/* ================= WORLD ================= */

export class World {
  constructor(scene, quality) {
    this.scene = scene;
    this.q = quality;
    this.interactables = [];
    this.clickMeshes = [];
    this.updaters = [];
    this.env = new Env(this);

    this.mats = { wall: null, floor: null, ceil: null };
    this._build();
  }

  register(id, group, meta) {
    const rec = { id, group, ...meta };
    this.interactables.push(rec);
    group.traverse(o => { if (o.isMesh) { o.userData.oid = id; this.clickMeshes.push(o); } });
    return rec;
  }

  /* ---------- room shell ---------- */
  _build() {
    const g = new THREE.Group();
    this.root = g;
    this.scene.add(g);

    const RX = 296, RY = 104, RZ = 148;

    // floor
    const floorMat = std(0x9A7459, .85);
    this.mats.floor = floorMat;
    const floor = new THREE.Mesh(new THREE.PlaneGeometry(RX + 40, RZ + 60), floorMat);
    floor.rotation.x = -Math.PI / 2;
    floor.position.set(RX / 2, 0, RZ / 2 - 4);
    floor.receiveShadow = true;
    g.add(floor);
    this.floor = floor;

    // floorboards (thin dark strips)
    const seamMat = new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: .12 });
    for (let x = 0; x < RX; x += 11) {
      const s = new THREE.Mesh(new THREE.PlaneGeometry(.6, RZ + 60), seamMat);
      s.rotation.x = -Math.PI / 2; s.position.set(x, .05, RZ / 2 - 4);
      g.add(s);
    }

    // back wall with holes (window / mirror / door)
    const wallMat = std(0x93AEB8, .92);
    this.mats.wall = wallMat;
    const shape = new THREE.Shape();
    shape.moveTo(0, 0); shape.lineTo(RX, 0); shape.lineTo(RX, RY); shape.lineTo(0, RY); shape.closePath();
    const holes = [
      [[86, 38], [122, 76]],       // window
      [[270.5, 6], [277.5, 47]],   // mirror
      [[279, 0], [293, 52]],       // door
    ];
    for (const [[x0, y0], [x1, y1]] of holes) {
      const p = new THREE.Path();
      p.moveTo(x0, y0); p.lineTo(x0, y1); p.lineTo(x1, y1); p.lineTo(x1, y0); p.closePath();
      shape.holes.push(p);
    }
    const wallGeo = new THREE.ShapeGeometry(shape);
    const wall = new THREE.Mesh(wallGeo, wallMat);
    wall.position.z = 0;
    wall.receiveShadow = true;
    g.add(wall);
    this.wall = wall;

    // ceiling
    const ceilMat = std(0x7D95A0, .95);
    this.mats.ceil = ceilMat;
    const ceil = new THREE.Mesh(new THREE.PlaneGeometry(RX + 40, RZ + 60), ceilMat);
    ceil.rotation.x = Math.PI / 2;
    ceil.position.set(RX / 2, RY, RZ / 2 - 4);
    g.add(ceil);

    // side walls
    for (const [x, ry] of [[0, Math.PI / 2], [RX, -Math.PI / 2]]) {
      const w = plane(RZ + 60, RY, wallMat);
      w.rotation.y = ry;
      w.position.set(x, RY / 2, RZ / 2 - 4);
      w.receiveShadow = true;
      g.add(w);
    }

    // baseboard + crown
    const base = rbox(RX, 3, 2, .5, 0x000000, { mat: std(new THREE.Color(0x93AEB8).multiplyScalar(.62).getHex(), .9), cast: false });
    base.position.set(RX / 2, 1.5, 1);
    g.add(base);

    this._buildWindow();
    this._buildOutside();
    this._buildDoor();
    this._buildMirror();

    this._buildBed();
    this._buildWallArt();
    this._buildPlant();
    this._buildCharger();
    this._buildRug();
    this._buildTV();
    this._buildDesk();
    this._buildWorkbench();
    this._buildArcade();
    this._buildToys();
    this._buildKitchen();
    this._buildBox();
    this._buildLights();
    this._buildParticles();

    this._buildLightsRig();
  }

  /* ---------- window + outside ---------- */
  _buildWindow() {
    const g = new THREE.Group();
    this.root.add(g);
    const frame = 0xEDE6DA;
    const W = 86, W2 = 122, H0 = 38, H1 = 76;
    // frame pieces
    const add = (w, h, x, y, z = .6, d = 3) => {
      const m = rbox(w, h, d, .6, frame, { rough: .6 });
      m.position.set(x, y, z); g.add(m);
    };
    add(W2 - W + 4, 4, (W + W2) / 2, H1 + 1, .8);       // top
    add(W2 - W + 4, 4, (W + W2) / 2, H0 - 1, .8);       // bottom
    add(4, H1 - H0 + 6, W - 1, (H0 + H1) / 2, .8);
    add(4, H1 - H0 + 6, W2 + 1, (H0 + H1) / 2, .8);
    // mullions
    const mul = (w, h, x, y) => { const m = rbox(w, h, 2, .4, frame, { cast: false }); m.position.set(x, y, .5); g.add(m); };
    mul(1.6, H1 - H0, (W + W2) / 2, (H0 + H1) / 2);
    mul(W2 - W, 1.6, (W + W2) / 2, (H0 + H1) / 2);
    // sill
    const sill = rbox(W2 - W + 12, 3, 8, 1, 0xD8CFC2, { rough: .7 });
    sill.position.set((W + W2) / 2, H0 - 3, 4);
    g.add(sill);
    // curtains
    const curtMat = std(0xD4C6B6, .95);
    this.curtains = [];
    for (const side of [-1, 1]) {
      const c = rbox(10, H1 - H0 + 8, 3, 3, 0xD4C6B6, { mat: curtMat });
      c.position.set((W + W2) / 2 + side * (W2 - W) / 2 - side * 4, (H0 + H1) / 2 + 2, 3.2);
      g.add(c); this.curtains.push({ m: c, side });
    }
    const rod = rbox(W2 - W + 22, 1.6, 1.6, .7, WOOD_D);
    rod.position.set((W + W2) / 2, H1 + 5, 4);
    g.add(rod);

    // glass
    const glass = plane(W2 - W, H1 - H0, new THREE.MeshStandardMaterial({
      color: 0xCFE3EA, transparent: true, opacity: .16, roughness: .1, metalness: .1,
    }));
    glass.position.set((W + W2) / 2, (H0 + H1) / 2, .4);
    g.add(glass);
    this.glass = glass;

    this.register('window', g, { stand: { x: 104, z: 40 }, face: Math.PI, action: 'window', click: () => this.env.cycleWeather() });

    // rain on glass
    const rainGeo = new THREE.BufferGeometry();
    const N = 60;
    const pos = new Float32Array(N * 3);
    for (let i = 0; i < N; i++) {
      pos[i * 3] = rnd(W, W2); pos[i * 3 + 1] = rnd(H0, H1); pos[i * 3 + 2] = 1.2;
    }
    rainGeo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    const rain = new THREE.Points(rainGeo, new THREE.PointsMaterial({
      color: 0xCFE3F2, size: 2.4, transparent: true, opacity: 0, sizeAttenuation: true,
    }));
    g.add(rain);
    this.rain = { points: rain, pos, N, W, W2, H0, H1 };
  }

  _buildOutside() {
    const g = new THREE.Group();
    g.position.z = -2;
    this.root.add(g);
    this.outside = g;

    // sky
    this.skyMat = new THREE.MeshBasicMaterial({ color: 0x8EC5E8, toneMapped: false, fog: false });
    const sky = plane(400, 240, this.skyMat);
    sky.position.set(104, 60, -90);
    g.add(sky);

    // hills
    const hillShape = new THREE.Shape();
    hillShape.moveTo(-200, -60);
    for (let i = 0; i <= 40; i++) {
      const x = -200 + i * 10;
      hillShape.lineTo(x, Math.sin(i * .7) * 8 + Math.sin(i * .31) * 12 + 4);
    }
    hillShape.lineTo(200, -60); hillShape.closePath();
    this.hillMat = std(0x7FA7A0, 1, 0, { });
    this.hillMat.fog = false;
    const hills = new THREE.Mesh(new THREE.ShapeGeometry(hillShape), this.hillMat);
    hills.position.set(104, 34, -70);
    g.add(hills);

    // sun / moon
    this.sun = new THREE.Mesh(new THREE.CircleGeometry(9, 24), new THREE.MeshBasicMaterial({ color: 0xFFE6A8, toneMapped: false }));
    this.sun.position.set(84, 78, -88);
    g.add(this.sun);
    this.sunGlow = new THREE.Mesh(new THREE.CircleGeometry(22, 24), new THREE.MeshBasicMaterial({
      color: 0xFFE6A8, transparent: true, opacity: .3, blending: THREE.AdditiveBlending, depthWrite: false, toneMapped: false,
    }));
    this.sunGlow.position.set(84, 78, -87);
    g.add(this.sunGlow);
    this.moon = new THREE.Mesh(new THREE.CircleGeometry(6, 24), new THREE.MeshBasicMaterial({ color: 0xF4EFD8, toneMapped: false }));
    this.moon.position.set(132, 80, -88);
    g.add(this.moon);

    // stars
    const sg = new THREE.BufferGeometry();
    const sp = new Float32Array(48 * 3);
    for (let i = 0; i < 48; i++) { sp[i * 3] = rnd(-80, 290); sp[i * 3 + 1] = rnd(46, 108); sp[i * 3 + 2] = -86; }
    sg.setAttribute('position', new THREE.BufferAttribute(sp, 3));
    this.stars = new THREE.Points(sg, new THREE.PointsMaterial({ color: 0xFFFFFF, size: 1.6, transparent: true, opacity: 0, sizeAttenuation: true }));
    g.add(this.stars);

    // clouds
    this.clouds = [];
    for (let i = 0; i < 4; i++) {
      const c = new THREE.Group();
      const m = std(0xFFFFFF, 1, 0, { extra: { transparent: true, opacity: .92 } });
      for (let j = 0; j < 4; j++) {
        const p = new THREE.Mesh(new THREE.SphereGeometry(rnd(6, 11), 10, 8), m);
        p.position.set(j * 9 - 12 + rnd(-3, 3), rnd(-2, 3), rnd(-2, 2));
        p.scale.y = .6;
        c.add(p);
      }
      c.position.set(rnd(0, 296), rnd(84, 100), -80 - i * 2);
      g.add(c);
      this.clouds.push(c);
    }

    // little tree
    const trunk = rbox(4, 26, 4, 1.5, 0x5E4A3A);
    trunk.position.set(150, 44, -46);
    g.add(trunk);
    this.treeLeaves = [];
    for (let i = 0; i < 5; i++) {
      const l = new THREE.Mesh(new THREE.SphereGeometry(rnd(8, 13), 12, 10), std(0x5F8F66, .95));
      l.position.set(150 + rnd(-9, 9), 58 + rnd(-4, 8), -46 + rnd(-4, 4));
      g.add(l); this.treeLeaves.push(l);
    }

    // rain outside
    const N = 260;
    const rg = new THREE.BufferGeometry();
    const rp = new Float32Array(N * 3);
    for (let i = 0; i < N; i++) { rp[i * 3] = rnd(70, 140); rp[i * 3 + 1] = rnd(30, 90); rp[i * 3 + 2] = rnd(-40, -6); }
    rg.setAttribute('position', new THREE.BufferAttribute(rp, 3));
    this.rainOut = new THREE.Points(rg, new THREE.PointsMaterial({ color: 0xBFD6EA, size: 1.6, transparent: true, opacity: 0, sizeAttenuation: true }));
    g.add(this.rainOut);
    this.rainOutData = { rp, N };
  }

  _buildDoor() {
    const g = new THREE.Group();
    this.root.add(g);
    const X0 = 279, X1 = 293, H = 52;
    // hallway behind
    const hall = plane(X1 - X0 + 6, H + 4, std(0x2A3350, 1, 0, { extra: { } }));
    hall.position.set((X0 + X1) / 2, H / 2, -22);
    g.add(hall);
    this.hallMat = hall.material;
    // frame
    const fr = 0xEDE6DA;
    const top = rbox(X1 - X0 + 6, 3, 4, .5, fr); top.position.set((X0 + X1) / 2, H + 1, .8); g.add(top);
    for (const x of [X0 - 1, X1 + 1]) { const j = rbox(3, H, 4, .5, fr); j.position.set(x, H / 2, .8); g.add(j); }
    // leaf (hinged at X1)
    const leaf = new THREE.Group();
    leaf.position.set(X1, 0, 1.4);
    const panel = rbox(X1 - X0, H, 2.4, 1, 0x7F5D48, { rough: .8 });
    panel.position.set(-(X1 - X0) / 2, H / 2, 0);
    leaf.add(panel);
    const knob = new THREE.Mesh(new THREE.SphereGeometry(1.4, 12, 10), std(0xE8C98A, .4, .5));
    knob.position.set(-(X1 - X0) + 3, 24, 1.6);
    leaf.add(knob);
    g.add(leaf);
    this.doorLeaf = leaf;
    this.doorOpen = 0;

    // mat
    const mat = new THREE.Mesh(new THREE.CircleGeometry(7, 20), std(0x8C6A5A, .95, 0, { extra: { transparent: true, opacity: .8 } }));
    mat.rotation.x = -Math.PI / 2; mat.scale.set(1.3, 1, .8);
    mat.position.set((X0 + X1) / 2, .1, 9);
    g.add(mat);

    this.register('door', g, { stand: { x: 286, z: 34 }, face: Math.PI, action: 'door' });
  }

  _buildMirror() {
    const g = new THREE.Group();
    this.root.add(g);
    const X0 = 270.5, X1 = 277.5, Y0 = 6, Y1 = 47;
    // dark backdrop behind wall (only visible through the hole)
    const back = plane(60, 70, std(0x1A2233, 1, 0, { extra: { } }));
    back.position.set((X0 + X1) / 2, 30, -34);
    g.add(back);
    this.mirrorBackdrop = back;
    // frame
    const frame = rbox(X1 - X0 + 4, Y1 - Y0 + 4, 3, 1.4, 0xB98A55, { rough: .6 });
    frame.position.set((X0 + X1) / 2, (Y0 + Y1) / 2, .8);
    g.add(frame);
    // inner dark so frame doesn't cover hole: rebuild frame as 4 bars
    g.remove(frame);
    const bar = (w, h, x, y) => { const b = rbox(w, h, 3.4, 1.2, 0xB98A55, { rough: .6 }); b.position.set(x, y, .9); g.add(b); };
    bar(X1 - X0 + 5, 3, (X0 + X1) / 2, Y1 + 1);
    bar(X1 - X0 + 5, 3, (X0 + X1) / 2, Y0 - 1);
    bar(3, Y1 - Y0 + 6, X0 - 1, (Y0 + Y1) / 2);
    bar(3, Y1 - Y0 + 6, X1 + 1, (Y0 + Y1) / 2);

    // glass
    const glass = plane(X1 - X0, Y1 - Y0, new THREE.MeshStandardMaterial({
      color: 0xCFE3EA, transparent: true, opacity: .2, roughness: .12, metalness: .3,
    }));
    glass.position.set((X0 + X1) / 2, (Y0 + Y1) / 2, .5);
    g.add(glass);
    this.mirrorGlass = glass;
    // shine
    const shine = plane(X1 - X0 - 1, (Y1 - Y0) * .9, new THREE.MeshBasicMaterial({
      color: 0xFFFFFF, transparent: true, opacity: .1, blending: THREE.AdditiveBlending, depthWrite: false, toneMapped: false,
    }));
    shine.rotation.z = .5;
    shine.position.set((X0 + X1) / 2 - 1, (Y0 + Y1) / 2 + 4, .7);
    g.add(shine);
    this.mirrorShine = shine;
    // mystery shimmer
    this.mirrorShimmer = new THREE.Mesh(new THREE.PlaneGeometry(X1 - X0, Y1 - Y0), new THREE.MeshBasicMaterial({
      color: 0x9FF3E0, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false, toneMapped: false,
    }));
    this.mirrorShimmer.position.set((X0 + X1) / 2, (Y0 + Y1) / 2, .6);
    g.add(this.mirrorShimmer);

    this.mirrorBounds = { x0: X0, x1: X1, y0: Y0, y1: Y1 };
    this.register('mirror', g, { stand: { x: 274, z: 30 }, face: Math.PI, action: 'mirror' });
  }

  /* ---------- furniture ---------- */

  _buildBed() {
    const g = new THREE.Group();
    this.root.add(g);
    // headboard at left end
    const hb = rbox(5, 30, 30, 2, WOOD_D, { rough: .8 });
    hb.position.set(5, 15, 19);
    g.add(hb);
    // rail
    const rail = rbox(46, 7, 30, 2, WOOD, { rough: .8 });
    rail.position.set(28, 7, 19);
    g.add(rail);
    // mattress
    const matt = rbox(44, 6, 28, 2.4, 0xE9E2D6, { rough: .9 });
    matt.position.set(28, 13.5, 19);
    g.add(matt);
    // duvet
    this.duvet = rbox(26, 7, 28, 2.6, 0x86A5BA, { rough: .95 });
    this.duvet.position.set(38, 17, 19);
    g.add(this.duvet);
    // pillow
    const pil = rbox(11, 5, 14, 2.4, 0xF7F2EA, { rough: .95 });
    pil.position.set(11, 19, 19);
    g.add(pil);
    // footboard
    const fb = rbox(3, 8, 30, 1.5, WOOD_D, { rough: .8 });
    fb.position.set(50, 5, 19);
    g.add(fb);

    this.register('bed', g, { stand: { x: 30, z: 44 }, face: Math.PI, action: 'sleep' });
  }

  _buildWallArt() {
    const g = new THREE.Group();
    this.root.add(g);
    // crayon drawings above bed + by plant
    const spots = [[12, 58, -3], [24, 62, 2], [36, 56, -2], [46, 64, 3], [72, 46, -2], [79, 52, 3]];
    const crayons = [0xE0706A, 0x6FA8E8, 0x7FB77E, 0xF3C35A, 0xC99BFF, 0xFF9E6B];
    spots.forEach(([x, y, rot], i) => {
      const paper = plane(9, 11, std(0xEDE3D2, .95));
      paper.position.set(x, y, .4);
      paper.rotation.z = rot * .03;
      g.add(paper);
      const scrib = new THREE.Mesh(new THREE.CircleGeometry(2.6, 12), new THREE.MeshBasicMaterial({
        color: crayons[i % 6], transparent: true, opacity: .85, toneMapped: false,
      }));
      scrib.position.set(x + (i % 2 ? .8 : -.6), y + (i % 3 - 1) * .8, .6);
      scrib.scale.set(1, .8, 1);
      g.add(scrib);
      const pin = new THREE.Mesh(new THREE.CircleGeometry(.8, 10), new THREE.MeshBasicMaterial({ color: 0xE0706A, toneMapped: false }));
      pin.position.set(x, y + 5.4, .7);
      g.add(pin);
    });

    // pennant string
    const cordPts = [];
    const flags = [0xE0706A, 0x6FA8E8, 0x7FB77E, 0xF3C35A, 0xC99BFF, 0xFF9E6B, 0x8FF5E2, 0xFFC27A];
    for (let i = 0; i <= 16; i++) {
      const t = i / 16;
      cordPts.push(new THREE.Vector3(8 + t * 80, 84 - Math.sin(t * Math.PI) * 5, .6));
    }
    const cord = new THREE.Line(
      new THREE.BufferGeometry().setFromPoints(cordPts),
      new THREE.LineBasicMaterial({ color: 0x5E4A3A })
    );
    g.add(cord);
    this.pennants = [];
    for (let i = 0; i < 8; i++) {
      const t = (i + .5) / 8;
      const tri = new THREE.Shape();
      tri.moveTo(-3.4, 0); tri.lineTo(3.4, 0); tri.lineTo(0, -7.5); tri.closePath();
      const f = new THREE.Mesh(new THREE.ShapeGeometry(tri), std(flags[i], .9, 0, { extra: { side: THREE.DoubleSide } }));
      f.position.set(8 + t * 80, 84 - Math.sin(t * Math.PI) * 5, .7);
      g.add(f);
      this.pennants.push({ m: f, ph: i * 1.3 });
    }

    // clock
    const clk = new THREE.Group();
    const face = new THREE.Mesh(new THREE.CircleGeometry(6.5, 26), std(0xF4EFE6, .8));
    face.position.z = .5;
    const rim = new THREE.Mesh(new THREE.TorusGeometry(6.5, .9, 8, 26), std(WOOD_D, .7));
    const h1 = plane(.8, 4.4, new THREE.MeshBasicMaterial({ color: INK }));
    h1.position.set(0, 1.8, .7);
    const h2 = plane(.7, 5.6, new THREE.MeshBasicMaterial({ color: 0xE0706A }));
    h2.position.set(0, 2.4, .8); h2.rotation.z = 1.9;
    clk.add(face, rim, h1, h2);
    clk.position.set(62, 60, .6);
    g.add(clk);
    this.clockHands = { h1, h2, clk };

    // corkboard
    const cb = rbox(30, 17, 1.6, 1, 0xC49A6C, { rough: .95 });
    cb.position.set(141, 49, .8);
    g.add(cb);
    const cbb = rbox(32, 19, 1.2, 1.4, WOOD_D, { rough: .85 });
    cbb.position.set(141, 49, .3);
    g.add(cbb);
    for (let i = 0; i < 4; i++) {
      const ph = plane(6.5, 5, std(0xF7F4EE, .9));
      ph.position.set(131 + i * 7, 50 + (i % 2 ? 2.5 : -2), 1.7);
      ph.rotation.z = (i % 2 ? .1 : -.12);
      g.add(ph);
      const pin = new THREE.Mesh(new THREE.CircleGeometry(.7, 8), new THREE.MeshBasicMaterial({ color: 0xE0706A, toneMapped: false }));
      pin.position.set(131 + i * 7, 52.2 + (i % 2 ? 2.5 : -2), 1.85);
      g.add(pin);
    }
    // sticky note
    const note = plane(5, 5, std(0xF3DB7A, .95));
    note.position.set(152, 44, 1.7); note.rotation.z = .07;
    g.add(note);

    // calendar
    const cal = plane(13, 15, std(0xF4EFE6, .9));
    cal.position.set(153, 84, .5);
    g.add(cal);
    const calHead = plane(13, 4, std(0xE0706A, .9));
    calHead.position.set(153, 89.5, .6);
    g.add(calHead);

    // blueprints
    const bp = plane(14, 18, std(0x6FA8E8, .9));
    bp.position.set(235, 43, .5);
    g.add(bp);
    const bpLine = plane(9, 9, new THREE.MeshBasicMaterial({ color: 0xE8F2FF, transparent: true, opacity: .5, toneMapped: false }));
    bpLine.position.set(235, 43, .6);
    g.add(bpLine);

    this.register('drawings', g, { stand: { x: 26, z: 42 }, face: Math.PI, action: 'drawings' });
  }

  _buildPlant() {
    const g = new THREE.Group();
    this.root.add(g);
    const pot = new THREE.Mesh(new THREE.CylinderGeometry(5.4, 4.2, 11, 18), std(0xB97E5E, .85));
    pot.position.set(61, 5.5, 12);
    pot.castShadow = pot.receiveShadow = true;
    g.add(pot);
    const rim = new THREE.Mesh(new THREE.CylinderGeometry(5.6, 5.6, 2, 18), std(0xA06A4E, .85));
    rim.position.set(61, 11, 12);
    g.add(rim);
    this.plantLeaves = [];
    const cols = [0x6FA27A, 0x86B98E];
    for (let i = 0; i < 7; i++) {
      const a = (-75 + i * 25) * Math.PI / 180;
      const leaf = new THREE.Mesh(new THREE.SphereGeometry(2.6, 10, 8), std(cols[i % 2], .9));
      leaf.scale.set(1, 2.4, .5);
      leaf.position.set(61 + Math.sin(a) * 5, 16 + Math.cos(a) * 4, 12 + Math.cos(a) * 1.5);
      leaf.rotation.z = -a * .8;
      leaf.castShadow = true;
      g.add(leaf);
      this.plantLeaves.push({ m: leaf, a, i });
    }
    this.register('plant', g, { stand: { x: 61, z: 34 }, face: Math.PI, action: 'inspect' });
  }

  _buildCharger() {
    const g = new THREE.Group();
    this.root.add(g);
    const pad = new THREE.Mesh(new THREE.CylinderGeometry(9, 9, 1.6, 28), std(0x2B323E, .7, .3));
    pad.position.set(85, .9, 16);
    pad.receiveShadow = true;
    g.add(pad);
    this.chargeRing = new THREE.Mesh(new THREE.RingGeometry(6.6, 8.4, 30), new THREE.MeshBasicMaterial({
      color: 0x7FE3F0, transparent: true, opacity: .4, side: THREE.DoubleSide, toneMapped: false,
    }));
    this.chargeRing.rotation.x = -Math.PI / 2;
    this.chargeRing.position.set(85, 1.9, 16);
    g.add(this.chargeRing);
    const totem = rbox(4, 21, 4, 1, 0x2C3444, { rough: .6 });
    totem.position.set(93, 10.5, 10);
    g.add(totem);
    this.chargeBars = [];
    for (let i = 0; i < 4; i++) {
      const b = rbox(2.4, 2.6, 1, .4, 0x7FE3F0, { extra: { emissive: 0x7FE3F0, emissiveIntensity: 1.4 }, cast: false });
      b.position.set(93, 4.5 + i * 4, 12.2);
      g.add(b);
      this.chargeBars.push(b);
    }
    this.register('charger', g, { stand: { x: 85, z: 34 }, face: Math.PI, action: 'charge' });
  }

  _buildRug() {
    const outer = new THREE.Mesh(new THREE.CircleGeometry(28, 40), std(0x6E5A74, .98, 0, { extra: { transparent: true, opacity: .55 } }));
    outer.rotation.x = -Math.PI / 2;
    outer.scale.set(1, 1, .34);
    outer.position.set(106, .12, 70);
    outer.receiveShadow = true;
    this.root.add(outer);
    const inner = new THREE.Mesh(new THREE.CircleGeometry(22, 40), std(0x8C7690, .98, 0, { extra: { transparent: true, opacity: .5 } }));
    inner.rotation.x = -Math.PI / 2;
    inner.scale.set(1, 1, .3);
    inner.position.set(106, .16, 70);
    this.root.add(inner);
  }

  _buildTV() {
    const g = new THREE.Group();
    this.root.add(g);
    const stand = rbox(16, 5, 10, 1.2, WOOD, { rough: .8 });
    stand.position.set(114, 2.5, 10);
    g.add(stand);
    const cons = rbox(7.5, 2, 5, .6, 0x20242F, { rough: .6 });
    cons.position.set(114, 6, 10);
    g.add(cons);
    this.tvStrip = rbox(6, .4, .6, .2, 0x5AB8FF, { extra: { emissive: 0x5AB8FF, emissiveIntensity: 1.6 }, cast: false });
    this.tvStrip.position.set(114, 5.4, 12.6);
    g.add(this.tvStrip);

    const bezel = rbox(17, 11.5, 1.6, .8, 0x14171F, { rough: .5 });
    bezel.position.set(114, 13.5, 8);
    g.add(bezel);
    this.tvCanvas = canvasTex(160, 104, (x, w, h) => {
      x.fillStyle = '#12151C'; x.fillRect(0, 0, w, h);
    });
    const scr = new THREE.Mesh(new THREE.PlaneGeometry(15, 9.4),
      new THREE.MeshBasicMaterial({ map: this.tvCanvas.tex, toneMapped: false }));
    scr.position.set(114, 13.5, 8.9);
    g.add(scr);
    this.tvScreen = scr;
    this.tvOn = 0;
    this.tvTimer = 0;

    this.register('tv', g, { stand: { x: 114, z: 46 }, face: Math.PI, action: 'watch' });
  }

  _buildDesk() {
    const g = new THREE.Group();
    this.root.add(g);
    const top = rbox(34, 2.4, 22, 1, WOOD, { rough: .75 });
    top.position.set(140, 19, 13);
    g.add(top);
    for (const x of [126, 154]) {
      const leg = rbox(2.4, 18, 16, .8, WOOD_D, { rough: .8 });
      leg.position.set(x, 9, 13);
      g.add(leg);
    }
    const drawer = rbox(11, 9, 15, .8, WOOD, { rough: .8 });
    drawer.position.set(149, 13, 13);
    g.add(drawer);

    // monitor
    const mon = rbox(19, 14, 2, 1.6, 0x2C3444, { rough: .6 });
    mon.position.set(133, 29, 8);
    g.add(mon);
    this.monCanvas = canvasTex(128, 92, (x, w, h) => { x.fillStyle = '#123B3F'; x.fillRect(0, 0, w, h); });
    const mscr = new THREE.Mesh(new THREE.PlaneGeometry(16.4, 11), new THREE.MeshBasicMaterial({ map: this.monCanvas.tex, toneMapped: false }));
    mscr.position.set(133, 29, 9.1);
    g.add(mscr);
    this.monScreen = mscr;
    this.monOn = 0; this.monTimer = 0;
    const mbase = rbox(6, 4, 3, .8, 0x2C3444, { rough: .6 });
    mbase.position.set(133, 21.5, 8);
    g.add(mbase);

    // keyboard, books, lamp, coin jar
    const kb = rbox(13, 1.2, 6, .6, 0x3B4658, { rough: .7 });
    kb.position.set(134, 20.6, 18);
    g.add(kb);
    const bk1 = rbox(2, 5, 7, .5, 0x6F8FA6); bk1.position.set(150, 22.7, 11); g.add(bk1);
    const bk2 = rbox(2, 6, 7, .5, 0xB97E5E); bk2.position.set(147.6, 23.2, 11); g.add(bk2);

    const lampBase = rbox(5.5, 1.4, 5.5, .7, 0x3B4658);
    lampBase.position.set(151, 20.6, 19);
    g.add(lampBase);
    const arm = new THREE.Mesh(new THREE.CylinderGeometry(.5, .5, 12, 8), std(0x8E99A8, .5, .4));
    arm.position.set(151, 26.5, 19);
    g.add(arm);
    const shade = new THREE.Mesh(new THREE.CylinderGeometry(2.4, 4.4, 5, 16, 1, true), std(0xE7C27A, .8, 0, { extra: { side: THREE.DoubleSide } }));
    shade.position.set(151, 33, 19);
    g.add(shade);
    this.lampBulb = new THREE.Mesh(new THREE.SphereGeometry(1.5, 12, 10), new THREE.MeshBasicMaterial({ color: 0xFFF0C8, toneMapped: false }));
    this.lampBulb.position.set(151, 31.5, 19);
    g.add(this.lampBulb);

    const jar = new THREE.Mesh(new THREE.CylinderGeometry(2.4, 2.4, 5, 16, 1, true), new THREE.MeshStandardMaterial({
      color: 0xE8C98A, transparent: true, opacity: .4, roughness: .1, side: THREE.DoubleSide,
    }));
    jar.position.set(143, 22.5, 8);
    g.add(jar);

    // Bolt helper screen on the desk
    const bolt = rbox(6.4, 5.2, 3, 1, INK, { rough: .6 });
    bolt.position.set(127, 23, 17);
    g.add(bolt);
    const bscr = plane(4.6, 3, new THREE.MeshBasicMaterial({ color: 0x0E1A22, toneMapped: false }));
    bscr.position.set(127, 23.2, 18.6);
    g.add(bscr);
    this.boltEyes = [];
    for (const s of [-1, 1]) {
      const e = plane(.9, 1.3, new THREE.MeshBasicMaterial({ color: 0x8FF5E2, toneMapped: false }));
      e.position.set(127 + s * 1.2, 23.4, 18.7);
      g.add(e); this.boltEyes.push(e);
    }

    this.register('desk', g, { stand: { x: 137, z: 40 }, face: Math.PI, action: 'computer' });
  }

  _buildWorkbench() {
    const g = new THREE.Group();
    this.root.add(g);
    // pegboard
    const peg = rbox(36, 23, 1.6, 1, 0xC9B79C, { rough: .95 });
    peg.position.set(181, 35, .9);
    g.add(peg);
    const holeMat = new THREE.MeshBasicMaterial({ color: 0x8E7A62, transparent: true, opacity: .5, toneMapped: false });
    for (let i = 0; i < 9; i++) for (let j = 0; j < 5; j++) {
      const h = new THREE.Mesh(new THREE.CircleGeometry(.5, 6), holeMat);
      h.position.set(166 + i * 4, 27 + j * 4, 1.8);
      g.add(h);
    }
    // tools
    const w1 = rbox(1.4, 9, 1, .4, 0x8E99A8, { metal: .6, rough: .35 }); w1.position.set(170, 35, 2.2); g.add(w1);
    const sd = rbox(1.6, 8, 1.2, .5, 0xE0706A); sd.position.set(177, 34, 2.2); g.add(sd);
    const hm = rbox(6, 2.4, 1.6, .6, 0x6B7482, { metal: .5, rough: .4 }); hm.position.set(186, 38, 2.2); g.add(hm);
    const hmh = rbox(1.8, 8, 1.4, .5, WOOD_D); hmh.position.set(186, 33, 2.2); g.add(hmh);

    // bench
    const top = rbox(44, 3, 20, 1, 0x9A7A60, { rough: .8 });
    top.position.set(181, 17, 12);
    g.add(top);
    for (const x of [163, 199]) {
      const leg = rbox(3, 15.5, 16, .8, WOOD_D, { rough: .8 });
      leg.position.set(x, 7.75, 12);
      g.add(leg);
    }
    const shelf = rbox(40, 2, 14, .6, WOOD_D, { rough: .85 });
    shelf.position.set(181, 5, 12);
    g.add(shelf);
    const vice = rbox(5, 4, 4, .8, 0x6B7482, { metal: .5, rough: .4 });
    vice.position.set(163, 20, 14);
    g.add(vice);
    // parts
    const partCols = [0xB8C2CC, 0x7FD6A8, 0xE0975C, 0x4F7A5F, 0xA7B0BA];
    for (let i = 0; i < 5; i++) {
      const p = rbox(3.4, 2.4, 3.4, .8, partCols[i], { rough: .6 });
      p.position.set(170 + i * 5.4, 20, 8);
      g.add(p);
    }

    // shelf of discoveries (above bench)
    const plank = rbox(40, 2, 8, .6, WOOD, { rough: .8 });
    plank.position.set(181, 58, 5);
    g.add(plank);
    for (const x of [166, 196]) {
      const br = rbox(2, 8, 6, .6, WOOD_D, { rough: .8 });
      br.position.set(x, 54, 5);
      g.add(br);
    }
    const itemCols = [0xB8C2CC, 0x7FD6A8, 0xE0975C, 0x9BE8D8, 0xD8B25A, 0x6FA8E8, 0xE0706A, 0xA9D8F0];
    this.shelfItems = [];
    for (let i = 0; i < 8; i++) {
      const it = rbox(3.6, 3.6, 3.6, 1, itemCols[i], { rough: .55 });
      it.position.set(166 + i * 4.6, 61, 5);
      g.add(it);
      this.shelfItems.push(it);
    }

    this.register('workbench', g, { stand: { x: 181, z: 40 }, face: Math.PI, action: 'build' });
  }

  _buildArcade() {
    const g = new THREE.Group();
    this.root.add(g);
    const body = rbox(17, 46, 16, 2, 0x4A4470, { rough: .7 });
    body.position.set(215, 23, 10);
    g.add(body);
    const marq = rbox(15, 5, 2, 1, 0xFFC27A, { extra: { emissive: 0xFFC27A, emissiveIntensity: .5 } });
    marq.position.set(215, 42, 17.4);
    g.add(marq);
    const panel = rbox(16, 3.5, 4, 1, 0x3A355C, { rough: .7 });
    panel.position.set(215, 22, 17);
    panel.rotation.x = -.25;
    g.add(panel);
    const joy = new THREE.Mesh(new THREE.SphereGeometry(1.2, 10, 8), std(0x3B4658, .5));
    joy.position.set(211, 24.4, 18);
    g.add(joy);
    const btnCols = [0xE0706A, 0xFFC27A, 0xFF8FA3];
    btnCols.forEach((c, i) => {
      const b = new THREE.Mesh(new THREE.SphereGeometry(1, 10, 8), std(c, .5));
      b.position.set(216 + i * 3, 24.2, 18.4);
      g.add(b);
    });
    this.arcCanvas = canvasTex(110, 100, (x, w, h) => { x.fillStyle = '#0E1320'; x.fillRect(0, 0, w, h); });
    const scr = new THREE.Mesh(new THREE.PlaneGeometry(13, 12), new THREE.MeshBasicMaterial({ map: this.arcCanvas.tex, toneMapped: false }));
    scr.position.set(215, 32, 17.6);
    g.add(scr);
    this.arcScreen = scr;
    this.arcOn = 0; this.arcT = 0;

    this.register('arcade', g, { stand: { x: 215, z: 38 }, face: Math.PI, action: 'arcade' });
  }

  _buildToys() {
    const g = new THREE.Group();
    this.root.add(g);
    const tbox = rbox(14, 11, 12, 1.4, 0xD6AE6A, { rough: .85 });
    tbox.position.set(234, 5.5, 10);
    g.add(tbox);
    const rocket = new THREE.Mesh(new THREE.ConeGeometry(3, 12, 12), std(0xDDE3EB, .7));
    rocket.position.set(234, 17, 10);
    rocket.castShadow = true;
    g.add(rocket);
    this.register('toys', g, { stand: { x: 234, z: 34 }, face: Math.PI, action: 'toys', nibOnly: true });

    // football (dynamic)
    const ball = new THREE.Mesh(new THREE.SphereGeometry(3, 18, 14), std(0xE98A7A, .6));
    ball.castShadow = true;
    ball.position.set(226, 3, 56);
    this.root.add(ball);
    this.ball = ball;
    this.ballVel = new THREE.Vector3();
    this.register('ball', ball, { stand: null, action: 'kick', dynamic: true });
  }

  _buildKitchen() {
    const g = new THREE.Group();
    this.root.add(g);
    // fridge
    const fridge = rbox(11, 27, 12, 1.8, 0xE8EEF2, { rough: .6 });
    fridge.position.set(250, 13.5, 8);
    g.add(fridge);
    const seam = rbox(11.2, .5, 12.2, .2, 0xAAB4BE, { cast: false });
    seam.position.set(250, 15, 8);
    g.add(seam);
    const h1 = rbox(.8, 5, .8, .3, 0x8E99A8, { metal: .5, rough: .4 });
    h1.position.set(245.5, 20, 14.2);
    g.add(h1);
    const mag = new THREE.Mesh(new THREE.CircleGeometry(.8, 10), new THREE.MeshBasicMaterial({ color: 0xE0706A, toneMapped: false }));
    mag.position.set(251, 22, 14.1);
    g.add(mag);
    const note = plane(3.6, 3.6, std(0xF3DB7A, .95));
    note.position.set(253, 18, 14.1); note.rotation.z = .06;
    g.add(note);

    // counter
    const ctop = rbox(17, 2, 16, .8, 0xA9866A, { rough: .75 });
    ctop.position.set(263, 18, 10);
    g.add(ctop);
    const cbody = rbox(16, 17, 15, 1, 0x7A8E91, { rough: .8 });
    cbody.position.set(263, 8.5, 10);
    g.add(cbody);
    // hot plate + pot
    const hp = rbox(8, 1.4, 7, .5, 0x2C3444, { rough: .6 });
    hp.position.set(261, 19.7, 9);
    g.add(hp);
    this.hotPlate = rbox(7, .4, 6, .3, 0xFF8A4C, { extra: { emissive: 0xFF8A4C, emissiveIntensity: 0 }, cast: false });
    this.hotPlate.position.set(261, 20.3, 9);
    g.add(this.hotPlate);
    const pot = new THREE.Mesh(new THREE.CylinderGeometry(3, 3, 4.4, 16), std(0x9AA7B8, .4, .5));
    pot.position.set(261, 22.4, 9);
    pot.castShadow = true;
    g.add(pot);
    this.pot = pot;
    // steam
    this.steam = [];
    for (let i = 0; i < 4; i++) {
      const s = new THREE.Mesh(new THREE.SphereGeometry(1.2, 8, 6), new THREE.MeshBasicMaterial({
        color: 0xFFFFFF, transparent: true, opacity: 0, depthWrite: false, toneMapped: false,
      }));
      s.position.set(261, 25, 9);
      g.add(s); this.steam.push(s);
    }

    this.register('fridge', g, { stand: { x: 250, z: 36 }, face: Math.PI, action: 'fridge' });
    this.fridgeGroup = g;
    // fridge door hinged
    const fdoor = new THREE.Group();
    fdoor.position.set(244.5, 0, 14);
    const leaf = rbox(11, 27, 1.6, 1.4, 0xE8EEF2, { rough: .6 });
    leaf.position.set(5.5, 13.5, 0);
    fdoor.add(leaf);
    g.add(fdoor);
    this.fridgeDoor = fdoor;
    this.fridgeOpen = 0;
    // interior glow
    this.fridgeGlow = new THREE.PointLight(0xE6F2FF, 0, 40, 2);
    this.fridgeGlow.position.set(251, 16, 8);
    g.add(this.fridgeGlow);
  }

  _buildBox() {
    const g = new THREE.Group();
    this.root.add(g);
    const b = rbox(12, 10, 12, .6, 0xC9A57A, { rough: .95 });
    b.position.set(272, 5, 30);
    g.add(b);
    const inner = rbox(10, 1, 10, .4, 0x5E4A36, { cast: false });
    inner.position.set(272, 9.4, 30);
    g.add(inner);
    this.register('box', g, { stand: null, action: 'box', nibOnly: true });
  }

  _buildLights() {
    // string lights along the ceiling
    this.bulbs = [];
    const palette = [0xFFD27A, 0xFF9E8A, 0x9FF3E0, 0xFFE9B0];
    for (let s = 0; s < 3; s++) {
      const x0 = 20 + s * 95;
      const pts = [];
      for (let i = 0; i <= 12; i++) {
        const t = i / 12;
        pts.push(new THREE.Vector3(x0 + t * 74, 99 - Math.sin(t * Math.PI) * 4, 3));
      }
      const wire = new THREE.Line(
        new THREE.BufferGeometry().setFromPoints(pts),
        new THREE.LineBasicMaterial({ color: 0x2B3342, transparent: true, opacity: .55 })
      );
      this.root.add(wire);
      for (let i = 0; i < 4; i++) {
        const t = (i + .5) / 4;
        const col = palette[(i + s) % 4];
        const b = new THREE.Mesh(new THREE.SphereGeometry(1.5, 10, 8), new THREE.MeshStandardMaterial({
          color: 0xE8E2D6, emissive: col, emissiveIntensity: .4, roughness: .5,
        }));
        b.position.set(x0 + t * 74, 99 - Math.sin(t * Math.PI) * 4 - 1.8, 3);
        this.root.add(b);
        this.bulbs.push({ m: b, col, ph: i + s * 1.7 });
      }
    }
  }

  _buildParticles() {
    // dust motes
    const N = this.q.low ? 40 : 90;
    const g = new THREE.BufferGeometry();
    const p = new Float32Array(N * 3);
    for (let i = 0; i < N; i++) { p[i * 3] = rnd(10, 286); p[i * 3 + 1] = rnd(6, 96); p[i * 3 + 2] = rnd(8, 120); }
    g.setAttribute('position', new THREE.BufferAttribute(p, 3));
    this.dust = new THREE.Points(g, new THREE.PointsMaterial({
      color: 0xFFFFFF, size: 1.4, transparent: true, opacity: .16, sizeAttenuation: true, depthWrite: false,
    }));
    this.root.add(this.dust);
    this.dustData = { p, N };

    // light shaft from window
    const shaftGeo = new THREE.BufferGeometry();
    const verts = new Float32Array([
      86, 38, 2, 122, 38, 2, 150, 0, 96,
      86, 38, 2, 150, 0, 96, 78, 0, 96,
      86, 76, 2, 122, 76, 2, 150, 44, 96,
      86, 76, 2, 150, 44, 96, 78, 44, 96,
    ]);
    shaftGeo.setAttribute('position', new THREE.BufferAttribute(verts, 3));
    this.shaft = new THREE.Mesh(shaftGeo, new THREE.MeshBasicMaterial({
      color: 0xFFF1C8, transparent: true, opacity: .1, blending: THREE.AdditiveBlending,
      depthWrite: false, side: THREE.DoubleSide, toneMapped: false,
    }));
    this.root.add(this.shaft);

    // sun patch on floor
    const patchGeo = new THREE.BufferGeometry();
    patchGeo.setAttribute('position', new THREE.BufferAttribute(new Float32Array([
      88, .3, 44, 124, .3, 44, 156, .3, 112,
      88, .3, 44, 156, .3, 112, 74, .3, 112,
    ]), 3));
    this.sunPatch = new THREE.Mesh(patchGeo, new THREE.MeshBasicMaterial({
      color: 0xFFF1C8, transparent: true, opacity: .16, blending: THREE.AdditiveBlending,
      depthWrite: false, side: THREE.DoubleSide, toneMapped: false,
    }));
    this.root.add(this.sunPatch);

    // lamp pool at night
    this.lampPool = new THREE.Mesh(new THREE.CircleGeometry(26, 28), new THREE.MeshBasicMaterial({
      color: 0xFFB866, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false, toneMapped: false,
    }));
    this.lampPool.rotation.x = -Math.PI / 2;
    this.lampPool.position.set(151, .25, 34);
    this.root.add(this.lampPool);
  }

  _buildLightsRig() {
    this.hemi = new THREE.HemisphereLight(0xBFD6E8, 0x4A4038, .75);
    this.scene.add(this.hemi);
    this.amb = new THREE.AmbientLight(0x6B7690, .35);
    this.scene.add(this.amb);

    this.sun = new THREE.DirectionalLight(0xFFF6E4, 1.1);
    this.sun.position.set(60, 150, -120);
    this.sun.target.position.set(120, 0, 60);
    this.sun.castShadow = true;
    const sm = this.q.shadow;
    this.sun.shadow.mapSize.set(sm, sm);
    const c = this.sun.shadow.camera;
    c.left = -190; c.right = 190; c.top = 150; c.bottom = -150; c.near = 10; c.far = 460;
    this.sun.shadow.bias = -0.0012;
    this.sun.shadow.normalBias = .8;
    this.scene.add(this.sun, this.sun.target);

    this.moon = new THREE.DirectionalLight(0xB8C8FF, 0);
    this.moon.position.set(160, 130, -100);
    this.moon.target.position.set(140, 0, 50);
    this.scene.add(this.moon, this.moon.target);

    this.lamp = new THREE.PointLight(0xFFB866, 0, 150, 1.8);
    this.lamp.position.set(151, 34, 24);
    this.scene.add(this.lamp);

    this.tvLight = new THREE.PointLight(0x7FB8FF, 0, 90, 2);
    this.tvLight.position.set(114, 16, 22);
    this.scene.add(this.tvLight);

    this.arcLight = new THREE.PointLight(0x8FF5E2, 0, 80, 2);
    this.arcLight.position.set(215, 32, 24);
    this.scene.add(this.arcLight);

    this.monLight = new THREE.PointLight(0x9FE7E0, 0, 70, 2);
    this.monLight.position.set(133, 30, 22);
    this.scene.add(this.monLight);

    this.scene.fog = null;
  }

  /* ---------- runtime ---------- */

  setQualityMotion(t) { void t; }

  update(dt, t) {
    const env = this.env;
    env.update(dt);
    const day = env.day, night = env.night, ev = env.evening;

    // walls / floor / ceiling color lerps
    this.mats.wall.color.copy(lerpC(0x26304A, 0x93AEB8, day));
    this.mats.floor.color.copy(lerpC(0x3F332E, 0x9A7459, day));
    this.mats.ceil.color.copy(lerpC(0x1C2336, 0x7D95A0, day));

    // lights
    const duskCol = lerpC(0xFFF6E4, 0xFFB27A, ev);
    if (env.weather === 'rain') duskCol.lerp(new THREE.Color(0xB9C4D6), .5);
    if (env.weather === 'cloudy') duskCol.lerp(new THREE.Color(0xD8DEE8), .3);
    this.sun.color.copy(duskCol);
    this.sun.intensity = 1.25 * day;
    this.sun.position.set(lerp(20, 130, clamp((env.hour - 6) / 12, 0, 1)), lerp(70, 170, Math.sin(clamp((env.hour - 6) / 12, 0, 1) * Math.PI)), -140);
    this.moon.intensity = .34 * night;
    this.hemi.intensity = lerp(.28, .8, day);
    this.hemi.color.copy(lerpC(0x2A3452, 0xBFD6E8, day));
    this.amb.intensity = lerp(.22, .4, day);
    this.amb.color.copy(lerpC(0x232B45, 0x6B7690, day));

    this.lamp.intensity = 620 * night;
    this.lampBulb.material.color.setHex(night > .3 ? 0xFFF0C8 : 0x9C9384);
    this.lampPool.material.opacity = .22 * night;

    const tvGlow = this.tvOn * (.25 + night * .75);
    this.tvLight.intensity = 260 * tvGlow;
    this.tvStrip.material.emissiveIntensity = .6 + Math.sin(t * 3) * .2;
    this.arcLight.intensity = 190 * night * (0.6 + 0.4 * Math.sin(t * 1.7));
    this.monLight.intensity = 150 * (this.monOn ? (.3 + night * .7) : 0);

    // string bulbs
    for (const b of this.bulbs) {
      b.m.material.emissiveIntensity = .35 + night * (1.5 + Math.sin(t * 1.3 + b.ph) * .35);
    }

    // window visuals
    const rainOn = env.weather === 'rain';
    const cloudy = env.weather === 'cloudy';
    const skyDay = lerpC(0x8EC5E8, 0x5A6E82, cloudy ? .5 : 0);
    const skyCol = day > .05 ? lerpC(0x1D2A4F, skyDay.getHex(), day) : lerpC(0x0E1733, 0x2A3763, 0);
    if (env.hour > 16.5 && env.hour < 20.5 && day > .05) {
      skyCol.lerp(new THREE.Color(0xF2A07B), .45 * ev);
    }
    this.skyMat.color.copy(skyCol);
    this.hillMat.color.copy(lerpC(0x1A2440, 0x7FA7A0, day * (rainOn ? .5 : 1)));
    this.stars.material.opacity = night * .9;
    this.sun.visible = day > .05;
    this.sunGlow.visible = day > .05;
    this.sunGlow.material.opacity = .3 * day;
    this.moon.visible = night > .15;
    for (const c of this.clouds) {
      c.position.x += dt * (1.4 + (rainOn ? 3 : 0)) * (c.position.z < -80 ? .4 : .7);
      if (c.position.x > 330) c.position.x = -40;
      c.children.forEach(m => m.material.color.setHex(rainOn ? 0x8E99AB : cloudy ? 0xC7CFDA : 0xFFFFFF));
    }
    for (const l of this.treeLeaves) {
      l.material.color.copy(lerpC(0x1E2A38, 0x5F8F66, day));
      l.rotation.z = Math.sin(t * .8 + l.position.y) * .05 * (rainOn ? 2.4 : 1);
    }

    // rain particles
    this.rain.points.material.opacity += ((rainOn ? .75 : 0) - this.rain.points.material.opacity) * (1 - Math.exp(-4 * dt));
    const rp = this.rain.pos;
    if (this.rain.points.material.opacity > .02) {
      for (let i = 0; i < this.rain.N; i++) {
        rp[i * 3 + 1] -= dt * 70;
        if (rp[i * 3 + 1] < this.rain.H0) { rp[i * 3 + 1] = this.rain.H1; rp[i * 3] = rnd(this.rain.W, this.rain.W2); }
      }
      this.rain.points.geometry.attributes.position.needsUpdate = true;
    }
    const ro = this.rainOutData;
    this.rainOut.material.opacity += ((rainOn ? .6 : 0) - this.rainOut.material.opacity) * (1 - Math.exp(-4 * dt));
    if (this.rainOut.material.opacity > .02) {
      for (let i = 0; i < ro.N; i++) {
        ro.rp[i * 3 + 1] -= dt * 95;
        ro.rp[i * 3] += dt * 6;
        if (ro.rp[i * 3 + 1] < 24) { ro.rp[i * 3 + 1] = 95; ro.rp[i * 3] = rnd(70, 140); }
      }
      this.rainOut.geometry.attributes.position.needsUpdate = true;
    }

    // curtains sway
    const gust = rainOn ? 1 : cloudy ? .4 : .15;
    for (const c of this.curtains) {
      c.m.rotation.z = Math.sin(t * (0.5 + gust * 1.3) + c.side) * 0.045 * (0.4 + gust);
      c.m.position.x += Math.sin(t * (0.5 + gust) + c.side * 2) * 0.004;
    }

    // shaft + sun patch
    this.shaft.material.opacity = .1 * day * (rainOn ? .35 : 1);
    this.shaft.material.color.setHex(ev > .3 ? 0xFFC08A : day > .3 ? 0xFFF1C8 : 0xB8C8FF);
    this.sunPatch.material.opacity = .17 * day * (rainOn ? .2 : 1);
    this.sunPatch.material.color.copy(this.shaft.material.color);
    const slant = ((env.hour - 13) / 6);
    this.sunPatch.position.x = clamp(-slant * 30, -34, 34);
    const nightAmt = night;
    this.shaft.material.blending = THREE.AdditiveBlending;

    // wall color cast by weather
    if (rainOn) {
      this.mats.wall.color.lerp(new THREE.Color(0x7D8CA0), .3);
      this.mats.floor.color.lerp(new THREE.Color(0x6B6157), .25);
    }

    // clock hands
    const hh = env.hour % 12, mm = (env.hour * 60) % 60;
    this.clockHands.h2.rotation.z = -(mm / 60) * Math.PI * 2;
    this.clockHands.h1.rotation.z = -(hh / 12) * Math.PI * 2;

    // dust
    const dd = this.dustData;
    for (let i = 0; i < dd.N; i++) {
      dd.p[i * 3 + 1] += Math.sin(t * .5 + i) * .01 + dt * .7;
      dd.p[i * 3] += Math.cos(t * .3 + i * 2) * .01;
      if (dd.p[i * 3 + 1] > 100) dd.p[i * 3 + 1] = 4;
    }
    this.dust.geometry.attributes.position.needsUpdate = true;
    this.dust.material.opacity = .1 + day * .1;

    // door
    const doorTarget = this.doorGoal ?? 0;
    this.doorOpen += (doorTarget - this.doorOpen) * (1 - Math.exp(-3 * dt));
    this.doorLeaf.rotation.y = -this.doorOpen * 1.15;
    this.hallMat.color.copy(lerpC(0x1B2440, 0xBFDCE6, day * .9));

    // fridge door
    this.fridgeOpen += ((this.fridgeGoal ?? 0) - this.fridgeOpen) * (1 - Math.exp(-5 * dt));
    this.fridgeDoor.rotation.y = -this.fridgeOpen * 1.5;
    this.fridgeGlow.intensity = this.fridgeOpen * 180;

    // hot plate + steam
    const cooking = this.cookGoal ?? 0;
    this.hotPlate.material.emissiveIntensity = cooking * (0.5 + Math.abs(Math.sin(t * 6)) * .5);
    this.steam.forEach((s, i) => {
      const k = (t * .5 + i / 4) % 1;
      s.material.opacity = cooking * (1 - k) * .35;
      s.position.y = 25 + k * 9;
      s.position.x = 261 + Math.sin(t * 2 + i) * 1.6;
      s.scale.setScalar(.6 + k * 1.4);
    });

    // screens
    this._updateScreens(dt, t, night);

    // charge ring
    this.chargeRing.material.opacity = this.charging ? .4 + .45 * Math.abs(Math.sin(t * 2.2)) : .2;
    this.chargeBars.forEach((b, i) => {
      const on = this.charging ? ((t * 3 + i) % 4) < 3.2 : i < 3;
      b.material.emissiveIntensity = on ? 1.4 : .15;
    });

    // plant sway + pennants
    for (const l of this.plantLeaves) {
      l.m.rotation.z = -l.a * .8 + Math.sin(t * .8 + l.i) * .045;
    }
    for (const p of this.pennants) {
      p.m.rotation.y = Math.sin(t * .8 + p.ph) * .18;
      p.m.rotation.z = Math.sin(t * .6 + p.ph) * .05;
    }
    // bolt eyes slide
    this.boltEyes.forEach((e, i) => { e.position.x = 127 + (i ? 1.2 : -1.2) + Math.sin(t * .7 + i * 3) * .5; });

    // ball physics
    this._updateBall(dt);

    // arcades / mirror shimmer
    this.mirrorShimmer.material.opacity = this.mirrorMystery ? .12 + Math.abs(Math.sin(t * 2.4)) * .18 : 0;
  }

  _updateScreens(dt, t, night) {
    this.tvTimer -= dt;
    if (this.tvTimer <= 0 && this.tvOn) {
      this.tvTimer = .16;
      this.tvCanvas.redraw((x, w, h) => {
        x.fillStyle = '#070B1E'; x.fillRect(0, 0, w, h);
        // stars
        for (let i = 0; i < 26; i++) {
          x.fillStyle = `rgba(255,255,255,${.3 + Math.random() * .7})`;
          x.fillRect(Math.random() * w, Math.random() * h, 2, 2);
        }
        // ship
        const sx = (Math.sin(t * .7) * .4 + .5) * (w - 30) + 8;
        const sy = (Math.cos(t * .9) * .3 + .5) * (h - 40) + 14;
        x.fillStyle = '#FF6B6B'; x.fillRect(sx, sy, 14, 8);
        x.fillStyle = '#FFE066'; x.fillRect(sx + 14, sy + 2, 6, 4);
      });
    }
    this.monTimer -= dt;
    if (this.monTimer <= 0 && this.monOn) {
      this.monTimer = .22;
      this.monCanvas.redraw((x, w, h) => {
        x.fillStyle = '#154A4F'; x.fillRect(0, 0, w, h);
        x.fillStyle = 'rgba(159,231,224,.75)';
        for (let i = 0; i < 5; i++) {
          const len = 20 + ((Math.sin(t + i * 2) * .5 + .5) * 60);
          x.fillRect(10, 14 + i * 14, len, 5);
        }
        if (Math.sin(t * 4) > 0) x.fillRect(10 + 60, 14 + 5 * 14, 7, 7);
      });
    }
    this.arcT -= dt;
    if (this.arcT <= 0) {
      this.arcT = this.arcOn ? .12 : 1.2;
      this.arcCanvas.redraw((x, w, h) => {
        x.fillStyle = '#0E1320'; x.fillRect(0, 0, w, h);
        if (this.arcOn) {
          x.fillStyle = '#8FF5E2';
          const px = (Math.sin(t * 1.7) * .5 + .5) * (w - 24) + 6;
          const py = (Math.cos(t * 2.3) * .5 + .5) * (h - 34) + 6;
          x.fillRect(px, py, 14, 14);
          x.fillStyle = 'rgba(143,245,226,.55)';
          x.fillRect(8, h - 16, 40, 8);
          x.fillStyle = 'rgba(255,194,122,.9)';
          x.fillRect(52, h - 16, 26, 8);
        } else {
          x.fillStyle = 'rgba(143,245,226,.35)';
          x.fillRect(10, 44, 90, 12);
          x.fillRect(10, 64, 60, 8);
        }
        x.fillStyle = 'rgba(0,0,0,.18)';
        for (let i = 0; i < h; i += 5) x.fillRect(0, i, w, 2);
      });
    }
  }

  _updateBall(dt) {
    const b = this.ball, v = this.ballVel;
    if (v.lengthSq() > .01) {
      b.position.addScaledVector(v, dt);
      v.multiplyScalar(Math.exp(-1.6 * dt));
      b.rotation.x += v.z * dt * .3;
      b.rotation.z -= v.x * dt * .3;
      if (b.position.x < 6) { b.position.x = 6; v.x = Math.abs(v.x) * .6; }
      if (b.position.x > 290) { b.position.x = 290; v.x = -Math.abs(v.x) * .6; }
      if (b.position.z < 6) { b.position.z = 6; v.z = Math.abs(v.z) * .6; }
      if (b.position.z > 138) { b.position.z = 138; v.z = -Math.abs(v.z) * .6; }
      b.position.y = 3 + Math.abs(Math.sin(performance.now() * .006)) * Math.min(6, v.length() * .25);
    } else {
      b.position.y += (3 - b.position.y) * (1 - Math.exp(-8 * dt));
    }
  }
}
