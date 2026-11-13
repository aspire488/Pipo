import * as THREE from 'three';
import { mulberry } from './util.js';

/*
 * Forest Hills: the outdoor place, in 3D. Pine hills, a clearing for Pipo, deer, rabbits,
 * robins, birds crossing the sky, an owl and fireflies after dusk. All procedural and
 * instanced, so it stays cheap on phones. Same room-unit scale as the house (Pipo is 40 tall).
 */

const CENTER = new THREE.Vector3(148, 0, 60);   // the clearing; inside the camera rig's bounds

function hillHeight(x, z) {
  const dx = x - CENTER.x, dz = z - CENTER.z;
  const r = Math.hypot(dx, dz);
  const clearing = Math.min(1, Math.max(0, (r - 70) / 120));      // flat where he stands
  const rolls = Math.sin(x * 0.012) * 18 + Math.cos(z * 0.017 + 1.3) * 14 + Math.sin((x + z) * 0.006) * 26;
  const back = Math.max(0, -dz - 120) * 0.12;                        // hills rise gently behind him
  return (rolls + 20 + back) * clearing;
}

function pineGeometry() {
  const parts = [];
  const trunk = new THREE.CylinderGeometry(1.2, 1.6, 10, 6); trunk.translate(0, 5, 0);
  parts.push([trunk, 0x5A3E2B]);
  for (let k = 0; k < 3; k++) {
    const c = new THREE.ConeGeometry(11 - k * 2.6, 16 - k * 2, 7); c.translate(0, 14 + k * 9, 0);
    parts.push([c, k === 2 ? 0x4C8A57 : 0x3F7A4C]);
  }
  // merge with vertex colors (one draw call for every pine)
  let count = 0; for (const [g] of parts) count += g.toNonIndexed().attributes.position.count;
  const pos = new Float32Array(count * 3), col = new Float32Array(count * 3), nor = new Float32Array(count * 3);
  let o = 0;
  for (const [g0, hex] of parts) {
    const g = g0.toNonIndexed(); g.computeVertexNormals();
    const c = new THREE.Color(hex);
    pos.set(g.attributes.position.array, o * 3); nor.set(g.attributes.normal.array, o * 3);
    for (let i = 0; i < g.attributes.position.count; i++) col.set([c.r, c.g, c.b], (o + i) * 3);
    o += g.attributes.position.count;
  }
  const out = new THREE.BufferGeometry();
  out.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  out.setAttribute('normal', new THREE.BufferAttribute(nor, 3));
  out.setAttribute('color', new THREE.BufferAttribute(col, 3));
  return out;
}

function part(geo, color, x, y, z, opts = {}) {
  const m = new THREE.Mesh(geo, new THREE.MeshStandardMaterial({ color, roughness: 0.8, ...opts }));
  m.position.set(x, y, z); m.castShadow = true;
  return m;
}

class Deer {
  constructor(x, z, seed) {
    this.root = new THREE.Group();
    const fur = 0xB07A4A, dark = 0x6B4A30;
    const body = part(new THREE.CapsuleGeometry(4.2, 9, 4, 10), fur, 0, 13, 0); body.rotation.z = Math.PI / 2; this.root.add(body);
    this.neck = new THREE.Group(); this.neck.position.set(6.5, 15, 0); this.root.add(this.neck);
    const neck = part(new THREE.CylinderGeometry(1.6, 2.2, 9, 8), fur, 0, 4, 0); neck.rotation.z = -0.35; this.neck.add(neck);
    const head = part(new THREE.CapsuleGeometry(2, 3.4, 4, 8), fur, 2.6, 8.6, 0); head.rotation.z = Math.PI / 2 - 0.3; this.neck.add(head);
    for (const s of [-1, 1]) this.neck.add(part(new THREE.ConeGeometry(0.9, 3.2, 6), fur, 1, 11, s * 1.6));
    this.neck.add(part(new THREE.SphereGeometry(0.5, 8, 6), 0x1E1A16, 5.2, 9.2, 0));
    for (const [lx, lz] of [[-5, -2], [-5, 2], [5, -2], [5, 2]]) this.root.add(part(new THREE.CylinderGeometry(0.75, 0.6, 11, 6), dark, lx, 5.5, lz));
    this.root.add(part(new THREE.SphereGeometry(1.4, 8, 6), 0xF4EFE6, -7.4, 14.5, 0));
    this.root.position.set(x, hillHeight(x, z), z);
    this.root.rotation.y = seed * 6;
    this.seed = seed;
  }
  update(t) {
    // grazes, then lifts its head to look around
    const look = Math.sin(t * 0.35 + this.seed * 9) > 0.4;
    const goal = look ? 0.15 : 1.25;
    this.neck.rotation.z += (-(goal) - this.neck.rotation.z) * 0.04;
  }
}

class Rabbit {
  constructor(x, z, seed) {
    this.root = new THREE.Group();
    const c = 0xD8CFC2;
    this.root.add(part(new THREE.SphereGeometry(2.6, 10, 8), c, 0, 2.4, 0));
    this.root.add(part(new THREE.SphereGeometry(1.7, 10, 8), c, 2.2, 4.2, 0));
    for (const s of [-1, 1]) { const e = part(new THREE.CapsuleGeometry(0.45, 2.4, 3, 6), c, 2.2, 7, s * 0.7); e.rotation.x = s * 0.15; this.root.add(e); }
    this.root.add(part(new THREE.SphereGeometry(0.9, 8, 6), 0xFFFFFF, -2.6, 2.8, 0));
    this.home = new THREE.Vector3(x, 0, z); this.seed = seed;
    this.root.position.set(x, hillHeight(x, z), z);
  }
  update(t) {
    const cyc = (t * 0.6 + this.seed * 3) % 6;
    const hopping = cyc < 1.6;
    const ang = this.seed * 7 + Math.floor((t * 0.6 + this.seed * 3) / 6) * 1.7;
    if (hopping) {
      const d = cyc / 1.6;
      this.root.position.x = this.home.x + Math.cos(ang) * d * 12;
      this.root.position.z = this.home.z + Math.sin(ang) * d * 12;
      this.root.rotation.y = -ang;
    }
    this.root.position.y = hillHeight(this.root.position.x, this.root.position.z) + (hopping ? Math.abs(Math.sin(cyc * Math.PI * 2.5)) * 3 : 0);
  }
}

export class ForestHills {
  constructor({ low = false } = {}) {
    this.scene = new THREE.Scene();
    this.scene.fog = new THREE.Fog(0x9FC3D6, 220, 760);
    const rnd = mulberry(7);

    // sky dome with a vertical gradient
    this.skyUniforms = { top: { value: new THREE.Color(0x6FA8DC) }, bottom: { value: new THREE.Color(0xCFE6F0) } };
    const sky = new THREE.Mesh(new THREE.SphereGeometry(900, 24, 12), new THREE.ShaderMaterial({
      uniforms: this.skyUniforms, side: THREE.BackSide, depthWrite: false, fog: false,
      vertexShader: 'varying float vY; void main(){ vY = normalize(position).y; gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.); }',
      fragmentShader: 'uniform vec3 top; uniform vec3 bottom; varying float vY; void main(){ gl_FragColor = vec4(mix(bottom, top, smoothstep(-0.05, 0.6, vY)), 1.); }',
    }));
    sky.position.copy(CENTER);
    this.scene.add(sky);

    // rolling ground
    const g = new THREE.PlaneGeometry(1400, 1000, low ? 70 : 120, low ? 50 : 90);
    g.rotateX(-Math.PI / 2);
    const p = g.attributes.position;
    const colors = new Float32Array(p.count * 3);
    const grassA = new THREE.Color(0x6FAE5C), grassB = new THREE.Color(0x4F8A4A), tmp = new THREE.Color();
    for (let i = 0; i < p.count; i++) {
      const x = p.getX(i) + CENTER.x, z = p.getZ(i) + CENTER.z;
      const h = hillHeight(x, z);
      p.setY(i, h);
      tmp.copy(grassA).lerp(grassB, Math.min(1, h / 60 + rnd() * 0.15));
      colors.set([tmp.r, tmp.g, tmp.b], i * 3);
    }
    g.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    g.computeVertexNormals();
    g.translate(CENTER.x, 0, CENTER.z);
    this.ground = new THREE.Mesh(g, new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.95 }));
    this.ground.receiveShadow = true;
    this.scene.add(this.ground);

    // pines, instanced
    const pine = pineGeometry();
    const n = low ? 160 : 260;
    this.pines = new THREE.InstancedMesh(pine, new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.9, flatShading: true }), n);
    this.pines.castShadow = !low; this.pines.receiveShadow = true;
    const m = new THREE.Matrix4(), q = new THREE.Quaternion(), s = new THREE.Vector3(), v = new THREE.Vector3();
    let placed = 0;
    while (placed < n) {
      const x = CENTER.x + (rnd() - 0.5) * 1100, z = CENTER.z + (rnd() - 0.75) * 800;
      if (Math.hypot(x - CENTER.x, (z - CENTER.z) * 1.2) < 150 || z > CENTER.z + 120) continue;   // keep the clearing and the camera side open
      const sc = 0.8 + rnd() * 1.1;
      m.compose(v.set(x, hillHeight(x, z) - 1, z), q.setFromAxisAngle(new THREE.Vector3(0, 1, 0), rnd() * 6), s.set(sc, sc * (0.9 + rnd() * 0.4), sc));
      this.pines.setMatrixAt(placed++, m);
    }
    this.scene.add(this.pines);

    // rocks + flowers in the clearing
    const rockGeo = new THREE.DodecahedronGeometry(4, 0);
    for (let i = 0; i < 9; i++) {
      const a = rnd() * 6.28, r = 50 + rnd() * 40;
      const rock = part(rockGeo, 0x8E9499, CENTER.x + Math.cos(a) * r, 1, CENTER.z + Math.sin(a) * r * 0.6, { flatShading: true });
      rock.scale.setScalar(0.5 + rnd()); rock.receiveShadow = true; this.scene.add(rock);
    }
    const flowerGeo = new THREE.SphereGeometry(1, 6, 4);
    const flowers = new THREE.InstancedMesh(flowerGeo, new THREE.MeshStandardMaterial({ roughness: 0.6 }), 80);
    for (let i = 0; i < 80; i++) {
      const a = rnd() * 6.28, r = 20 + rnd() * 70;
      m.compose(v.set(CENTER.x + Math.cos(a) * r, 0.8, CENTER.z + Math.sin(a) * r * 0.7), q.identity(), s.setScalar(0.7 + rnd() * 0.6));
      flowers.setMatrixAt(i, m);
      flowers.setColorAt(i, new THREE.Color([0xFFE07A, 0xF2A6C8, 0xFFFFFF, 0xC99BFF][i % 4]));
    }
    this.scene.add(flowers);

    // animals
    this.deer = [new Deer(CENTER.x - 70, CENTER.z - 30, 0.2), new Deer(CENTER.x + 85, CENTER.z - 45, 0.7)];
    this.rabbits = [0.1, 0.45, 0.8].map((sd, i) => new Rabbit(CENTER.x - 75 + i * 70, CENTER.z - 25 - (i % 2) * 18, sd));
    for (const a of [...this.deer, ...this.rabbits]) this.scene.add(a.root);

    // robins hopping on the grass
    this.robins = [];
    for (let i = 0; i < 4; i++) {
      const r = new THREE.Group();
      r.add(part(new THREE.SphereGeometry(1.4, 8, 6), 0x6B5444, 0, 1.4, 0));
      r.add(part(new THREE.SphereGeometry(0.9, 8, 6), 0xE07A4A, 0.8, 1.3, 0));
      r.add(part(new THREE.SphereGeometry(0.8, 8, 6), 0x6B5444, 1.2, 2.4, 0));
      r.userData = { x: CENTER.x - 30 + i * 22, z: CENTER.z + 35 + (i % 2) * 10, ph: i * 1.7 };
      this.robins.push(r); this.scene.add(r);
    }

    // birds crossing the sky: one instanced V-shape, wings flap by squashing
    const wing = new THREE.BufferGeometry();
    wing.setAttribute('position', new THREE.Float32BufferAttribute([0, 0, 0, -4, 0, -1.2, -0.6, 0, 1, 0, 0, 0, 4, 0, -1.2, 0.6, 0, 1], 3));
    wing.computeVertexNormals();
    this.birds = new THREE.InstancedMesh(wing, new THREE.MeshBasicMaterial({ color: 0x2E3440, side: THREE.DoubleSide }), 16);
    this.birdData = Array.from({ length: 16 }, (_, i) => ({ r: 160 + rnd() * 120, h: 95 + rnd() * 60, sp: 0.05 + rnd() * 0.05, ph: rnd() * 6.28, flap: 7 + rnd() * 4, dir: i % 3 ? 1 : -1 }));
    this.scene.add(this.birds);

    // owl on a stump (dusk/night), fireflies
    this.owl = new THREE.Group();
    this.owl.add(part(new THREE.CylinderGeometry(5, 6, 14, 10), 0x5A3E2B, 0, 7, 0));
    this.owl.add(part(new THREE.SphereGeometry(4, 10, 8), 0x8A7458, 0, 18, 0));
    for (const sx of [-1.5, 1.5]) this.owl.add(part(new THREE.SphereGeometry(1.1, 8, 6), 0xFFD36E, sx, 19.5, 3.2, { emissive: 0xFFD36E, emissiveIntensity: 0.6 }));
    this.owl.position.set(CENTER.x + 60, hillHeight(CENTER.x + 60, CENTER.z - 10), CENTER.z - 10);
    this.scene.add(this.owl);
    const ffGeo = new THREE.BufferGeometry();
    const ffPos = new Float32Array(60 * 3);
    for (let i = 0; i < 60; i++) ffPos.set([CENTER.x + (rnd() - 0.5) * 220, 6 + rnd() * 30, CENTER.z + (rnd() - 0.5) * 140], i * 3);
    ffGeo.setAttribute('position', new THREE.BufferAttribute(ffPos, 3));
    const dot = document.createElement('canvas'); dot.width = dot.height = 32;
    const dx = dot.getContext('2d'), grad = dx.createRadialGradient(16, 16, 0, 16, 16, 16);
    grad.addColorStop(0, 'rgba(255,255,255,1)'); grad.addColorStop(1, 'rgba(255,255,255,0)'); dx.fillStyle = grad; dx.fillRect(0, 0, 32, 32);
    this.fireflies = new THREE.Points(ffGeo, new THREE.PointsMaterial({ color: 0xFFF1A0, size: 3.5, map: new THREE.CanvasTexture(dot), transparent: true, opacity: 0, depthWrite: false, blending: THREE.AdditiveBlending }));
    this.scene.add(this.fireflies);

    // light
    this.hemi = new THREE.HemisphereLight(0xCFE6F0, 0x4F6B3A, 0.9);
    this.sun = new THREE.DirectionalLight(0xFFF2D6, 1.6);
    this.sun.position.set(CENTER.x - 120, 220, CENTER.z + 120);
    this.sun.target.position.copy(CENTER);
    this.sun.castShadow = true;
    this.sun.shadow.mapSize.set(low ? 1024 : 2048, low ? 1024 : 2048);
    Object.assign(this.sun.shadow.camera, { left: -160, right: 160, top: 160, bottom: -160, near: 10, far: 600 });
    this.scene.add(this.hemi, this.sun, this.sun.target);
    this.center = CENTER;
  }

  groundAt(x, z) { return hillHeight(x, z); }

  /** day 0..1 (night..day) from the app's clock + weather. */
  update(dt, t, day = 1) {
    const night = 1 - day;
    this.skyUniforms.top.value.setHex(0x6FA8DC).lerp(new THREE.Color(0x0E1733), night);
    this.skyUniforms.bottom.value.setHex(0xCFE6F0).lerp(new THREE.Color(0x2A3763), night);
    this.scene.fog.color.copy(this.skyUniforms.bottom.value);
    this.sun.intensity = 1.6 * day + 0.15;
    this.hemi.intensity = 0.35 + 0.6 * day;
    for (const d of this.deer) d.update(t);
    for (const r of this.rabbits) r.update(t);
    for (const r of this.robins) {
      const u = r.userData; const c = (t + u.ph) % 3;
      r.position.set(u.x + Math.sin(t * 0.3 + u.ph) * 8, Math.abs(Math.sin(c * 6)) * (c < 1 ? 1.6 : 0), u.z);
      r.rotation.y = Math.sin(t * 0.3 + u.ph) > 0 ? 0 : Math.PI;
      r.visible = day > 0.3;
    }
    const m = new THREE.Matrix4(), q = new THREE.Quaternion(), s = new THREE.Vector3(), v = new THREE.Vector3();
    this.birdData.forEach((b, i) => {
      const a = b.ph + t * b.sp * b.dir;
      v.set(CENTER.x + Math.cos(a) * b.r, b.h + Math.sin(t * 0.7 + i) * 4, CENTER.z - 80 + Math.sin(a) * b.r * 0.5);
      q.setFromAxisAngle(new THREE.Vector3(0, 1, 0), -a * b.dir + (b.dir > 0 ? Math.PI : 0));
      s.set(1.6, 1 + Math.sin(t * b.flap + i) * 0.9, 1.6);
      this.birds.setMatrixAt(i, m.compose(v, q, s));
    });
    this.birds.instanceMatrix.needsUpdate = true;
    this.birds.material.color.setHex(night > 0.6 ? 0x111522 : 0x2E3440);
    this.owl.visible = night > 0.35;
    this.fireflies.material.opacity = Math.max(0, night - 0.3) * (0.7 + 0.3 * Math.sin(t * 3));
  }
}
