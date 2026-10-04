import * as THREE from 'three';

const ROOM = { minX: 16, maxX: 280, minY: 6, maxY: 96, minZ: -6, maxZ: 134 };
const HOME = { tx: 148, ty: 28, tz: 36, az: 0.0, el: 0.46, dist: 360 };

const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
const damp = (cur, goal, lambda, dt) => cur + (goal - cur) * (1 - Math.exp(-lambda * dt));

export class CameraRig {
  constructor(camera, dom) {
    this.camera = camera;
    this.dom = dom;

    this.target = new THREE.Vector3(HOME.tx, HOME.ty, HOME.tz);
    this.goalTarget = this.target.clone();
    this.az = HOME.az; this.el = HOME.el; this.dist = HOME.dist;
    this.goalAz = HOME.az; this.goalEl = HOME.el; this.goalDist = HOME.dist;

    this.follow = false;
    this.idle = 0;
    this.userTouched = false;
    this.search = null;      // { legs:[{tx,ty,tz,az,el,dist,hold}], i:0, t:0 }
    this.followPos = new THREE.Vector3();

    this.pointers = new Map();
    this.mode = null;
    this.lastPinch = 0;
    this._bind();
  }

  _bind() {
    const dom = this.dom;
    dom.addEventListener('contextmenu', e => e.preventDefault());
    dom.addEventListener('pointerdown', e => {
      dom.setPointerCapture?.(e.pointerId);
      this.pointers.set(e.pointerId, { x: e.clientX, y: e.clientY, sx: e.clientX, sy: e.clientY, btn: e.button });
      this._beginGesture();
    });
    dom.addEventListener('pointermove', e => {
      const p = this.pointers.get(e.pointerId);
      if (!p) return;
      const dx = e.clientX - p.x, dy = e.clientY - p.y;
      p.x = e.clientX; p.y = e.clientY;
      if (this.pointers.size === 1) {
        const pan = p.btn === 2 || e.shiftKey || e.pointerType === 'touch' && e.isPrimary === false;
        if (pan) this._pan(dx, dy);
        else this._orbit(dx, dy);
      } else if (this.pointers.size === 2) {
        const pts = [...this.pointers.values()];
        const distNow = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y);
        if (this.lastPinch) {
          const scale = distNow / this.lastPinch;
          this.goalDist = clamp(this.goalDist / scale, 70, 480);
        }
        this.lastPinch = distNow;
        this._pan(dx * 0.5, dy * 0.5);
      }
      this.idle = 0;
      this.search = null;
    });
    const up = e => {
      this.pointers.delete(e.pointerId);
      if (this.pointers.size < 2) this.lastPinch = 0;
      if (this.pointers.size === 0) this.mode = null;
    };
    dom.addEventListener('pointerup', up);
    dom.addEventListener('pointercancel', up);
    dom.addEventListener('wheel', e => {
      e.preventDefault();
      this.goalDist = clamp(this.goalDist * (1 + Math.sign(e.deltaY) * 0.09), 70, 480);
      this.idle = 0; this.search = null;
    }, { passive: false });
    dom.addEventListener('dblclick', () => this.reset());
  }

  _beginGesture() {
    this.userTouched = true;
    this.idle = 0;
    this.search = null;
    document.getElementById('hint')?.classList.add('hidden');
  }

  _orbit(dx, dy) {
    this.goalAz = clamp(this.goalAz - dx * 0.005, -1.45, 1.45);
    this.goalEl = clamp(this.goalEl + dy * 0.004, 0.06, 1.25);
  }

  _pan(dx, dy) {
    const right = new THREE.Vector3(Math.cos(this.az), 0, -Math.sin(this.az));
    const up = new THREE.Vector3(0, 1, 0);
    const k = this.dist * 0.0011;
    this.goalTarget.addScaledVector(right, -dx * k);
    this.goalTarget.addScaledVector(up, dy * k);
    this._clampTarget();
  }

  _clampTarget() {
    this.goalTarget.x = clamp(this.goalTarget.x, ROOM.minX, ROOM.maxX);
    this.goalTarget.y = clamp(this.goalTarget.y, ROOM.minY, ROOM.maxY);
    this.goalTarget.z = clamp(this.goalTarget.z, ROOM.minZ, ROOM.maxZ);
  }

  reset() {
    this.follow = false;
    this.search = null;
    this.goalTarget.set(HOME.tx, HOME.ty, HOME.tz);
    this.goalAz = HOME.az; this.goalEl = HOME.el; this.goalDist = HOME.dist;
    document.getElementById('btnFollow')?.classList.remove('on');
  }

  setFollow(on) {
    this.follow = on;
    if (on) this.search = null;
  }

  // Natural camera hunt: a few gentle looks around, ending framed on Pipo.
  startSearch(pipoPos, opts = {}) {
    const px = pipoPos.x, pz = pipoPos.z;
    const side = px < 150 ? 1 : -1;
    const legs = [
      { tx: clamp(px - side * 90, 40, 250), ty: 34, tz: 52, az: side * 0.5, el: 0.34, dist: 300, hold: 0.75 },
      { tx: clamp(px + side * 70, 40, 250), ty: 26, tz: 46, az: -side * 0.42, el: 0.5, dist: 250, hold: 0.7 },
      { tx: px, ty: 22, tz: pz + 8, az: (Math.random() - 0.5) * 0.5, el: opts.low ? 0.2 : 0.36, dist: opts.close ? 130 : 165, hold: 1.4 },
    ];
    this.search = { legs, i: 0, t: 0 };
    this.follow = false;
    document.getElementById('btnFollow')?.classList.remove('on');
  }

  update(dt, t) {
    this.idle += dt;

    if (this.search) {
      const s = this.search;
      const leg = s.legs[s.i];
      if (s.i === s.legs.length - 1) { leg.tx = this.followPos.x; leg.tz = this.followPos.z + 8; }
      s.t += dt;
      const k = s.i === 0 ? 1.1 : 1.7;
      this.goalTarget.set(leg.tx, leg.ty, leg.tz);
      this.goalAz = leg.az; this.goalEl = leg.el; this.goalDist = leg.dist;
      if (s.t > (s.i === 0 ? 2.0 : 1.7) + leg.hold) {
        s.i++; s.t = 0;
        if (s.i >= s.legs.length) this.search = null;
      }
      void k;
    } else if (this.follow) {
      const goal = this.followPos;
      this.goalTarget.x = damp(this.goalTarget.x, goal.x, 1.6, dt);
      this.goalTarget.y = damp(this.goalTarget.y, goal.y + 22, 1.6, dt);
      this.goalTarget.z = damp(this.goalTarget.z, goal.z + 6, 1.6, dt);
      this._clampTarget();
      this.goalDist = damp(this.goalDist, 165, 1.2, dt);
    }

    // gentle cinematic breathing when untouched
    let azOff = 0, elOff = 0;
    if (!this.search && !this.follow && this.idle > 5) {
      const a = Math.min(1, (this.idle - 5) / 6);
      azOff = Math.sin(t * 0.11) * 0.05 * a;
      elOff = Math.sin(t * 0.07 + 1.3) * 0.02 * a;
    }

    this.target.x = damp(this.target.x, this.goalTarget.x, 3.2, dt);
    this.target.y = damp(this.target.y, this.goalTarget.y, 3.2, dt);
    this.target.z = damp(this.target.z, this.goalTarget.z, 3.2, dt);
    this.az = damp(this.az, this.goalAz, this.search ? 2.2 : 5.5, dt);
    this.el = damp(this.el, this.goalEl, this.search ? 2.2 : 5.5, dt);
    this.dist = damp(this.dist, this.goalDist, this.search ? 2.0 : 5.0, dt);

    const az = this.az + azOff, el = clamp(this.el + elOff, 0.04, 1.3);
    const ce = Math.cos(el);
    const pos = new THREE.Vector3(
      this.target.x + Math.sin(az) * ce * this.dist,
      this.target.y + Math.sin(el) * this.dist,
      this.target.z + Math.cos(az) * ce * this.dist
    );
    // keep the camera out of the walls/floor
    pos.y = Math.max(pos.y, 8);
    pos.z = Math.max(pos.z, -40);
    pos.x = clamp(pos.x, -60, 360);
    this.camera.position.copy(pos);
    this.camera.lookAt(this.target);
  }
}
