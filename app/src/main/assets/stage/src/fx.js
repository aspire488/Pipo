import * as THREE from 'three';
import { GLTFLoader } from '../vendor/GLTFLoader.js';

/*
 * In-world feedback for what Pipo is actually doing (models/lab.glb):
 *  - the status halo at his feet: one colour + motion per state
 *  - the holo panel beside his head: the tool that's running and how it went
 *  - the armor dock: where the suit stands when he isn't wearing it
 * States come from the host (the app's engine, or the preview host). Nothing here invents work.
 */

export const STATES = {
  idle:      { color: 0x8FF5E2, speed: 0.25, lit: 0.18, label: '' },
  listening: { color: 0x7CF29A, speed: 0,    lit: 0.7,  label: 'Listening' },
  thinking:  { color: 0xFFC27A, speed: 1.6,  lit: 0.85, label: 'Thinking' },
  working:   { color: 0xB59BFF, speed: 3.2,  lit: 0.95, label: 'Working' },
  speaking:  { color: 0x8FF5E2, speed: 0.6,  lit: 0.6,  label: '' },
  success:   { color: 0xFFD36E, speed: 0.8,  lit: 1,    label: 'Done' },
  error:     { color: 0xFF6B6B, speed: 0,    lit: 1,    label: 'Problem' },
};

export class StageFX {
  async load(scene, url = 'models/lab.glb') {
    const gltf = await new GLTFLoader().loadAsync(url);
    this.scene = scene;
    const g = gltf.scene;

    this.halo = g.getObjectByName('StatusHalo');
    this.segs = [];
    this.halo.traverse(o => { if (o.isMesh) { o.material = new THREE.MeshBasicMaterial({ color: 0x8FF5E2, transparent: true, opacity: 0.2, toneMapped: false, depthWrite: false }); this.segs.push(o); } });
    this.segs.sort((a, b) => a.name.localeCompare(b.name));
    this.halo.scale.setScalar(0.62);
    scene.add(this.halo);

    this.holo = g.getObjectByName('HoloPanel');
    const screen = this.holo.getObjectByName('Holo_Screen');
    this.canvas = document.createElement('canvas');
    this.canvas.width = 512; this.canvas.height = 280;
    this.ctx = this.canvas.getContext('2d');
    this.tex = new THREE.CanvasTexture(this.canvas);
    this.tex.colorSpace = THREE.SRGBColorSpace;
    // a plain +Z plane with standard UVs, so lookAt(camera) shows the text the right way round
    screen.geometry.dispose();
    screen.geometry = new THREE.PlaneGeometry(40, 22);
    screen.rotation.set(0, 0, 0);
    screen.material = new THREE.MeshBasicMaterial({ map: this.tex, transparent: true, toneMapped: false, depthWrite: false, side: THREE.DoubleSide });
    this.holo.traverse(o => { if (o.isMesh && o !== screen) o.material.transparent = true; });
    this.holo.scale.setScalar(0.45);
    this.holo.visible = false;
    scene.add(this.holo);

    this.dock = g.getObjectByName('ArmorDock');
    this.dockAnchor = this.dock.getObjectByName('Dock_Anchor');
    this.dock.traverse(o => { if (o.isMesh) { o.castShadow = true; o.receiveShadow = true; } });
    scene.add(this.dock);

    this.state = 'idle';
    this.stateT = 0;
    this.color = new THREE.Color(STATES.idle.color);
    this.panel = null;    // { title, detail, status: 'running'|'ok'|'fail' }
    this.panelT = 0;
    return this;
  }

  /** state: one of STATES; panel: what to show on the holo screen (or null). */
  set(state, panel) {
    if (!STATES[state]) state = 'idle';
    if (state !== this.state) { this.state = state; this.stateT = 0; }
    const key = panel ? `${panel.title}|${panel.detail}|${panel.status}` : '';
    if (key !== this.panelKey) {
      this.panelKey = key;
      this.panel = panel;
      if (panel) { this.panelT = 0; this._drawPanel(); }
    }
  }

  _drawPanel() {
    const x = this.ctx, W = 512, H = 280, p = this.panel;
    x.clearRect(0, 0, W, H);
    const col = p.status === 'fail' ? '#FF8A8A' : p.status === 'ok' ? '#FFD36E' : '#BFAEFF';
    x.fillStyle = 'rgba(14,24,40,0.78)';
    x.beginPath(); x.roundRect(6, 6, W - 12, H - 12, 26); x.fill();
    x.strokeStyle = col; x.lineWidth = 4; x.globalAlpha = 0.8; x.stroke(); x.globalAlpha = 1;
    // big and simple: it's read at a glance from across the room; the HUD pill and toast carry the details
    x.fillStyle = col;
    x.textAlign = 'center';
    x.font = '700 120px system-ui, sans-serif';
    x.fillText(p.status === 'fail' ? '✕' : p.status === 'ok' ? '✓' : '⋯', W / 2, 150);
    x.font = '700 56px system-ui, sans-serif';
    x.fillText(String(p.title).slice(0, 14), W / 2, 238);
    x.textAlign = 'left';
    this.tex.needsUpdate = true;
  }

  /** Dock shows the suit he owns but isn't wearing. */
  placeDockSuit(suitModel, visible) {
    if (!this.dockSuit && suitModel) {
      this.dockSuit = suitModel;
      this.dockAnchor.add(suitModel.root);
    }
    if (this.dockSuit) this.dockSuit.root.visible = visible;
  }

  update(dt, t, pipoPos, headPos, camera) {
    if (!this.halo) return;
    this.stateT += dt;
    const s = STATES[this.state];
    this.color.lerp(new THREE.Color(s.color), 1 - Math.exp(-6 * dt));
    this.halo.position.set(pipoPos.x, 0.4, pipoPos.z);
    this.halo.rotation.z = 0;
    this.halo.rotation.y += dt * s.speed * 0.5;
    const n = this.segs.length;
    this.segs.forEach((seg, i) => {
      let a;
      switch (this.state) {
        case 'thinking': case 'working': {
          const head = (t * s.speed * 2) % n;
          const d = (i - head + n) % n;
          a = s.lit * Math.max(0.12, 1 - d / 4);
          break;
        }
        case 'listening': a = s.lit * (0.45 + 0.55 * Math.abs(Math.sin(t * 4 + i * 0.52))); break;
        case 'speaking': a = s.lit * (0.5 + 0.5 * Math.abs(Math.sin(t * 9 + i))); break;
        case 'success': a = s.lit * Math.max(0, 1 - this.stateT / 1.6); break;
        case 'error': a = s.lit * (Math.sin(t * 22) > 0 ? 0.9 : 0.25) * Math.max(0.25, 1 - this.stateT / 3); break;
        default: a = s.lit * (0.6 + 0.4 * Math.sin(t * 1.2 + i * 0.5));
      }
      seg.material.opacity = a;
      seg.material.color.copy(this.color);
    });
    // success: the ring blooms outward once
    const bloom = this.state === 'success' ? 0.62 * (1 + Math.min(1, this.stateT * 2) * 0.35) : 0.62;
    this.halo.scale.setScalar(bloom);

    // holo panel floats beside his head and faces the camera
    const show = !!this.panel && (this.panel.status === 'running' || this.panelT < 4.5);
    this.panelT += dt;
    this.holo.visible = show;
    if (show) {
      this.holo.position.set(headPos.x + 21, headPos.y + 10 + Math.sin(t * 1.6) * 0.8, headPos.z + 4);
      this.holo.lookAt(camera.position);
      const fade = this.panel.status === 'running' ? 1 : Math.min(1, (4.5 - this.panelT) / 0.6);
      this.holo.traverse(o => { if (o.isMesh) o.material.opacity = fade * (o.name === 'Holo_Screen' ? 1 : 0.85); });
    }
    if (this.dockSuit) this.dockSuit.update(dt, t, {});
  }
}
