import * as THREE from 'three';
import { cloudTexture } from './textures.js';
import { mulberry } from './util.js';

/* A painted sky dome: horizon glow, sun disc, moon, stars, slow clouds. Fog colour is read from it. */

const VERT = `
varying vec3 vDir;
void main() {
  vDir = normalize(position);
  vec4 p = modelViewMatrix * vec4(position, 1.0);
  gl_Position = projectionMatrix * p;
  gl_Position.z = gl_Position.w; // always at the far plane
}`;
const FRAG = `
uniform vec3 top; uniform vec3 mid; uniform vec3 horizon; uniform vec3 sunCol;
uniform vec3 sunDir; uniform float sunAmt; uniform float haze;
varying vec3 vDir;
void main() {
  vec3 d = normalize(vDir);
  float h = d.y;
  vec3 c = mix(horizon, mid, smoothstep(-0.02, 0.22, h));
  c = mix(c, top, smoothstep(0.2, 0.75, h));
  c = mix(c, horizon * 0.85, smoothstep(0.0, -0.25, h));
  float s = max(dot(d, normalize(sunDir)), 0.0);
  c += sunCol * (pow(s, 6.0) * 0.32 + pow(s, 64.0) * 0.5) * sunAmt;
  c += sunCol * smoothstep(0.9993, 0.9997, s) * 1.6 * sunAmt;
  c = mix(c, horizon, haze * (1.0 - smoothstep(0.0, 0.5, h)));
  gl_FragColor = vec4(c, 1.0);
  #include <colorspace_fragment>
}`;

export class Sky {
  constructor(scene, low) {
    this.uni = {
      top: { value: new THREE.Color() }, mid: { value: new THREE.Color() }, horizon: { value: new THREE.Color() },
      sunCol: { value: new THREE.Color(0xFFE6A8) }, sunDir: { value: new THREE.Vector3(0, .5, -1) },
      sunAmt: { value: 1 }, haze: { value: 0 },
    };
    const mat = new THREE.ShaderMaterial({
      uniforms: this.uni, vertexShader: VERT, fragmentShader: FRAG,
      side: THREE.BackSide, depthWrite: false, fog: false,
    });
    this.dome = new THREE.Mesh(new THREE.SphereGeometry(3000, 32, 16), mat);
    this.dome.renderOrder = -10;
    this.dome.frustumCulled = false;
    scene.add(this.dome);

    // stars
    const R = mulberry(77);
    const N = low ? 260 : 520;
    const sp = new Float32Array(N * 3);
    for (let i = 0; i < N; i++) {
      const a = R() * Math.PI * 2, y = 0.08 + Math.pow(R(), .7) * 0.92;
      const r = Math.sqrt(1 - y * y);
      sp[i * 3] = Math.cos(a) * r * 2600; sp[i * 3 + 1] = y * 2600; sp[i * 3 + 2] = Math.sin(a) * r * 2600;
    }
    const sg = new THREE.BufferGeometry();
    sg.setAttribute('position', new THREE.BufferAttribute(sp, 3));
    this.stars = new THREE.Points(sg, new THREE.PointsMaterial({
      color: 0xffffff, size: 2.2, sizeAttenuation: false, transparent: true, opacity: 0, depthWrite: false, fog: false,
    }));
    this.stars.frustumCulled = false;
    scene.add(this.stars);

    // moon: a soft disc with a glow
    const moonTex = (() => {
      const c = document.createElement('canvas'); c.width = c.height = 128;
      const x = c.getContext('2d');
      const g = x.createRadialGradient(64, 64, 0, 64, 64, 64);
      g.addColorStop(0, 'rgba(255,250,232,1)'); g.addColorStop(.22, 'rgba(255,250,232,1)'); g.addColorStop(.25, 'rgba(255,248,225,.35)'); g.addColorStop(1, 'rgba(255,248,225,0)');
      x.fillStyle = g; x.fillRect(0, 0, 128, 128);
      x.fillStyle = 'rgba(200,200,190,.35)';
      x.beginPath(); x.arc(58, 58, 4, 0, 7); x.fill(); x.beginPath(); x.arc(70, 70, 3, 0, 7); x.fill();
      const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; return t;
    })();
    this.moon = new THREE.Sprite(new THREE.SpriteMaterial({ map: moonTex, transparent: true, depthWrite: false, fog: false, toneMapped: false }));
    this.moon.scale.setScalar(260);
    scene.add(this.moon);

    // clouds: painted billboards drifting far away
    this.clouds = [];
    const tex = [cloudTexture(3), cloudTexture(9), cloudTexture(21)];
    const n = low ? 7 : 12;
    for (let i = 0; i < n; i++) {
      const m = new THREE.SpriteMaterial({ map: tex[i % 3], transparent: true, depthWrite: false, fog: false, opacity: .95 });
      const s = new THREE.Sprite(m);
      const sc = 260 + R() * 320;
      s.scale.set(sc, sc * .5, 1);
      s.position.set(-1600 + R() * 3400, 420 + R() * 520, -1400 - R() * 900);
      s.userData.v = 4 + R() * 6;
      scene.add(s);
      this.clouds.push(s);
    }
    this.fogColor = new THREE.Color();
  }

  /** env: { day, night, ev (sunset 0..1), dawn, rain, cloudy, hour, sunDir } */
  update(dt, e) {
    const u = this.uni;
    const dayTop = new THREE.Color(0x5E9FD8), dayMid = new THREE.Color(0x9CCBEA), dayHor = new THREE.Color(0xDCEBF0);
    const nightTop = new THREE.Color(0x070B1E), nightMid = new THREE.Color(0x111A38), nightHor = new THREE.Color(0x26305A);
    const setTop = new THREE.Color(0x3B4F8A), setMid = new THREE.Color(0xD99A8C), setHor = new THREE.Color(0xFFC08A);
    u.top.value.copy(nightTop).lerp(dayTop, e.day);
    u.mid.value.copy(nightMid).lerp(dayMid, e.day);
    u.horizon.value.copy(nightHor).lerp(dayHor, e.day);
    const golden = Math.max(e.ev, e.dawn) * Math.min(1, e.day * 1.6 + .25);
    u.top.value.lerp(setTop, golden * .45);
    u.mid.value.lerp(setMid, golden * .7);
    u.horizon.value.lerp(setHor, golden * .85);
    const grey = Math.max(e.rain, e.cloudy * .55);
    if (grey > 0) {
      const g = new THREE.Color(0x8A97A8).multiplyScalar(.25 + .75 * e.day);
      u.top.value.lerp(g, grey * .75); u.mid.value.lerp(g, grey * .75); u.horizon.value.lerp(g, grey * .6);
    }
    u.sunDir.value.copy(e.sunDir);
    u.sunCol.value.setHex(0xFFE6A8).lerp(new THREE.Color(0xFF9A5C), golden);
    u.sunAmt.value = Math.max(0, Math.min(1, e.sunUp * 3)) * (1 - grey * .85);
    u.haze.value = e.rain * .5;

    this.fogColor.copy(u.horizon.value).lerp(u.mid.value, .35);

    this.stars.material.opacity = Math.max(0, e.night - .35) * 1.4 * (1 - e.rain) * (1 - e.cloudy * .6);
    this.stars.rotation.y += dt * 0.004;

    const md = e.moonDir;
    this.moon.position.copy(md).multiplyScalar(2400);
    this.moon.material.opacity = Math.max(0, e.night - .2) * (1 - e.rain * .8);
    this.moon.visible = this.moon.material.opacity > .01;

    const cc = new THREE.Color(0xffffff).lerp(new THREE.Color(0x5A6478), e.night * .85);
    cc.lerp(new THREE.Color(0xFFC8A8), golden * .6);
    if (grey > 0) cc.lerp(new THREE.Color(0x7E8898).multiplyScalar(.4 + .6 * e.day), grey);
    for (const c of this.clouds) {
      c.position.x += dt * c.userData.v * (1 + e.rain * 2 + e.wind * 2);
      if (c.position.x > 1900) c.position.x = -1900;
      c.material.color.copy(cc);
      c.material.opacity = .55 + .4 * Math.max(e.cloudy, e.rain, .35);
    }
  }
}
