// Regression smoke test for the 3D stage, driven like a user in headless Chrome.
//   cd tools/stage-preview && npm install && node server.mjs &  node smoke.mjs [outDir]
// Exits non-zero if the suit state, saved state and visible armor ever disagree, if a tool
// outcome isn't reported, or if the page throws. Screenshots + frames go to outDir.
import puppeteer from 'puppeteer-core';
import fs from 'node:fs';
import assert from 'node:assert/strict';

const out = process.argv[2] || 'smoke-out';
const URL = process.env.STAGE_URL || 'http://127.0.0.1:5178/?city=London';
fs.mkdirSync(out + '/frames', { recursive: true });
const browser = await puppeteer.launch({
  executablePath: process.env.CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: 'new',
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'],
});
const page = await browser.newPage();
await page.setViewport({ width: 412, height: 915, deviceScaleFactor: 1, isMobile: true, hasTouch: true });
const errors = [];
page.on('pageerror', e => errors.push(e.message));
page.on('console', m => { if (m.type() === 'error' && !/ERR_FAILED/.test(m.text())) errors.push(m.text()); });
await page.goto(URL, { waitUntil: 'networkidle0', timeout: 60000 });
await page.evaluate(() => localStorage.clear());
await page.reload({ waitUntil: 'networkidle0' });

let frame = 0, recording = true;
(async () => { while (recording) { await page.screenshot({ path: `${out}/frames/f${String(frame++).padStart(4, '0')}.jpg`, type: 'jpeg', quality: 72 }).catch(() => {}); await new Promise(r => setTimeout(r, 200)); } })();
const wait = ms => new Promise(r => setTimeout(r, ms));
const shot = name => page.screenshot({ path: `${out}/${name}.png` });
const toasts = [], said = [];
await page.exposeFunction('noteToast', t => toasts.push(t));
await page.exposeFunction('noteSay', t => said.push(t));
await page.evaluate(() => {
  new MutationObserver(m => m.forEach(r => r.addedNodes.forEach(n => n.textContent && noteToast(n.textContent)))).observe(document.getElementById('toasts'), { childList: true });
  new MutationObserver(m => m.forEach(r => r.addedNodes.forEach(n => n.textContent && noteSay(n.textContent)))).observe(document.getElementById('bubbles'), { childList: true });
});
const saidSince = n => said.slice(n).join(' | ');
const st = () => page.evaluate(() => ({
  mk: PipoStage.state.suit.mk, saved: JSON.parse(localStorage.getItem('pipo.suit') || '{"mk":0}').mk,
  worn: __pipoStageDebug.pipo.armor.filter(a => a.visible).length, total: __pipoStageDebug.pipo.armor.length,
  dock: !!__pipoStageDebug.fx.dockSuit?.root.visible, place: PipoStage.state.place, ai: PipoStage.state.ai.state,
}));
const agree = (s, label) => {
  assert.equal(s.mk, s.saved, `${label}: shown suit and saved suit differ`);
  assert.equal(s.worn, s.mk ? s.total : 0, `${label}: armor meshes don't match the suit state`);
  assert.equal(s.dock, !s.mk, `${label}: dock should hold the suit exactly when he isn't wearing it`);
};
const type = async t => { await page.click('#msg'); await page.type('#msg', t); await page.click('#btnSend'); };

await wait(3000); await shot('01_home');
agree(await st(), 'start');
await page.click('#qaSuit'); await wait(450); await shot('02_suit_equipping');
await wait(2600); await shot('03_suit_on');
let s = await st(); assert.equal(s.mk, 1, 'Suit button equips Mk I'); agree(s, 'suit on');
for (let i = 0; i < 4; i++) await page.click('#qaSuit');   // hammering the button must not desync anything
await wait(4000); agree(await st(), 'after rapid taps');
if ((await st()).mk === 0) { await type('suit up'); await wait(3500); }
s = await st(); assert.equal(s.mk, 1, '"suit up" equips'); agree(s, 'suit up');

// record every AI state the stage passes through (a fast answer can finish within one sample)
await page.evaluate(() => { window.__aiSeen = new Set(); setInterval(() => __aiSeen.add(PipoStage.state.ai.state), 30); });
const seen = () => page.evaluate(() => [...__aiSeen]);
let mark = said.length;
await type('What is the capital of France?'); await wait(400); await shot('04_thinking');
await wait(6500); await shot('05_answer');
assert.ok((await seen()).includes('thinking') && (await seen()).includes('speaking'), `thinking -> speaking states shown (saw ${await seen()})`);
assert.match(saidSince(mark), /paris/i, `answers an ordinary question (got: ${saidSince(mark)})`);
mark = said.length;
await type('and how many people live there?'); await wait(7000); await shot('06_followup');
assert.match(saidSince(mark), /million|people|\d/i, `follow-up keeps the context (got: ${saidSince(mark)})`);

await page.click('[data-act="weather"]'); await wait(500); await shot('07_tool_running');
assert.ok((await seen()).includes('working'), 'tool shows as working');
await wait(5000); await shot('08_tool_done');
assert.ok(toasts.some(t => /Weather|°C/.test(t)), 'weather outcome reported');

await type('take off the armor'); await wait(3500); await shot('09_suit_off_dock');
s = await st(); assert.equal(s.mk, 0, '"take off the armor" removes it (and doesn\u2019t fly)'); agree(s, 'suit off');

await page.setRequestInterception(true);
const block = req => (req.url().includes('/api/') ? req.abort() : req.continue());
page.on('request', block);
mark = said.length;
await type('why is the sky blue?'); await wait(2500); await shot('10_brain_offline_error');
assert.match(saidSince(mark), /online brain isn.t answering/i, 'offline brain is reported honestly');
page.off('request', block); await page.setRequestInterception(false);

await wait(2500);
await page.click('[data-act="forest"]'); await wait(16000); await shot('11_forest_hills');
assert.equal((await st()).place, 'forest', 'Forest Hills trip arrives');
await wait(4000); await shot('12_forest_hills_2');
await type('come home'); await wait(3000); await shot('13_home_again');
assert.equal((await st()).place, null, 'comes home');
agree(await st(), 'end');

recording = false; await wait(400);
const fps = await page.evaluate(() => window.__pipoFps);
await browser.close();
assert.deepEqual(errors, [], 'no page errors');
fs.writeFileSync(out + '/transcript.txt', [...said, '--- outcomes ---', ...toasts, ''].join('\n'));
console.log(`stage smoke: all checks passed (${frame} frames, ~${fps} fps in software rendering)`);
