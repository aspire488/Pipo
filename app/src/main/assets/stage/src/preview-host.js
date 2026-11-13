/*
 * Browser preview host. On a phone the app's engine drives the stage; in a desktop browser this
 * stands in for it so the stage can be reviewed before anything is installed. It is loaded only
 * when window.PipoAndroid is absent.
 *
 * What's real here: the chat (live Gemini/Groq through tools/stage-preview/server.mjs, using the
 * app's prompt), the weather (Open-Meteo), "find out" (live research call), the suit state
 * (persisted), and every success/failure shown. What's simplified: his autonomous life and poses
 * (the app's BehaviorEngine and Rig do that on Android).
 */

const STATIONS = { bed: 56, plant: 71, charger: 84, window: 104, desk: 138, workbench: 181, arcade: 215, toys: 225, kitchen: 262, door: 286, dock: 200 };
const LS = {
  get(k, d) { try { return JSON.parse(localStorage.getItem('pipo.' + k)) ?? d; } catch (_) { return d; } },
  set(k, v) { try { localStorage.setItem('pipo.' + k, JSON.stringify(v)); } catch (_) { /* private mode */ } },
};

/* ---------------- poses (Rig units: degrees, Pipo is 100 tall) ---------------- */
const BASE = { bob: 0, squash: 1, stretch: 1, lean: 0, headTilt: 0, headBob: 0, armL: 8, armR: 8, legL: 0, legR: 0, spread: 0,
  sit: 0, lie: 0, liePitch: 0, crouch: 0, yaw: 0, headYaw: 0, cross: 0, cover: 0, hold: 0, jump: 0, shake: 0, swing: 0, swingAmt: 0 };
const ANIM = {
  idle: (t) => ({ bob: Math.sin(t * 2.2) * 0.6, armL: 8 + Math.sin(t * 2.2) * 2, armR: 8 + Math.sin(t * 2.2 + 0.4) * 2, headTilt: Math.sin(t * 0.5) * 4, headYaw: Math.sin(t * 0.27) * 0.25 }),
  walk: (t) => ({ bob: Math.abs(Math.sin(t * 10)) * 2.2, swing: Math.sin(t * 10), swingAmt: 26, armL: 10, armR: 10, lean: 4 }),
  think: (t) => ({ armR: 128, hold: 0.35, headTilt: -10 + Math.sin(t * 1.4) * 3, headBob: -4, armL: 14, bob: Math.sin(t * 1.6) * 0.4 }),
  talk: (t) => ({ armL: 18 + Math.max(0, Math.sin(t * 3.1)) * 30, armR: 14 + Math.max(0, Math.sin(t * 2.3 + 1)) * 26, headBob: Math.sin(t * 7) * 2.5, bob: Math.sin(t * 3) * 0.8 }),
  listen: (t) => ({ lean: 7, headTilt: 12, armL: 10, armR: 10, bob: Math.sin(t * 2) * 0.4 }),
  work: (t) => ({ armL: 40 + Math.sin(t * 9) * 18, armR: 40 + Math.sin(t * 9 + 2) * 18, hold: 0.55, lean: 9, headBob: 6, bob: Math.abs(Math.sin(t * 9)) * 0.8 }),
  dance: (t) => ({ bob: Math.abs(Math.sin(t * 6)) * 5, armL: 60 + Math.sin(t * 6) * 70, armR: 60 - Math.sin(t * 6) * 70, headTilt: Math.sin(t * 6) * 14, yaw: Math.sin(t * 3) * 0.6, legL: Math.max(0, Math.sin(t * 6)) * 8, legR: Math.max(0, -Math.sin(t * 6)) * 8 }),
  celebrate: (t) => ({ armL: 160, armR: 160, jump: Math.abs(Math.sin(t * 7)) * 9, squash: 1 + Math.sin(t * 14) * 0.04 }),
  shrug: (t) => ({ armL: 48, armR: 48, headTilt: 14, headBob: -3, bob: Math.sin(t * 2) * 0.3 }),
  sleep: (t) => ({ lie: 1, liePitch: 0.05, bob: Math.sin(t * 1.2) * 0.6, armL: 6, armR: 6 }),
  spin: (t) => ({ yaw: t * 14, armL: 70, armR: 70, jump: 3 }),
  wave: (t) => ({ armR: 150 + Math.sin(t * 10) * 18, headTilt: 8, bob: Math.sin(t * 3) * 0.5 }),
};

export function start(apply, getState) {
  const st = {
    x: 148, goalX: 148, anim: 'idle', animUntil: 0, t: 0, clockT: 0,
    suit: LS.get('suit', { mk: 0, cardboard: false }),
    owned: 1,                  // the preview's Pipo has built Mk I
    asleep: false, away: false, place: null,
    history: [], busy: null, nextIdea: 6, sayN: 0, toastN: 0,
    log: LS.get('actions', []),
  };
  const mood = () => (st.asleep ? 'SLEEPY' : st.anim === 'dance' || st.anim === 'celebrate' ? 'EXCITED' : st.anim === 'think' ? 'THOUGHTFUL' : 'CURIOUS');

  /* ---------- outputs ---------- */
  const say = (text, who = 'pipo') => apply({ say: { id: ++st.sayN, who, text } });
  const toast = (text, kind) => apply({ toast: { id: ++st.toastN, text, kind } });
  const ai = (state, panel = null) => apply({ ai: { state, panel } });
  const play = (anim, seconds) => { st.anim = anim; st.animUntil = st.clockT + seconds; };
  const record = (entry) => { st.log.unshift({ ...entry, at: new Date().toISOString() }); st.log.length = Math.min(st.log.length, 30); LS.set('actions', st.log); };
  const pushSuit = () => apply({ suit: { mk: st.suit.mk, cardboard: st.suit.cardboard, owned: st.owned, ownedCardboard: false, flying: false } });

  /* ---------- typed actions ---------- */
  // Every capability: validated input -> run with timeout/cancel -> validated result -> honest outcome.
  const ACTIONS = {
    suit: {
      title: 'Suit', timeoutMs: 3000,
      input: () => null,
      run: async () => {
        const on = st.suit.mk === 0;
        if (on && st.owned < 1) throw new Error("He hasn't built any armor yet.");
        play('spin', 0.9);
        await wait(700);
        st.suit = on ? { mk: st.owned, cardboard: false } : { mk: 0, cardboard: false };
        LS.set('suit', st.suit);
        pushSuit();
        return { on, mk: st.suit.mk };
      },
      validate: (r) => {
        // the displayed suit and the saved suit must agree
        const shown = getState().suit;
        return shown.mk === st.suit.mk && LS.get('suit', {}).mk === st.suit.mk;
      },
      done: (r) => (r.on ? [`Mark One. Online.`, `Suit on: Mk ${['', 'I', 'II', 'III'][r.mk]}`] : ['Armor off. Clank. Regular Pipo again.', 'Suit off']),
    },
    weather: {
      title: 'Weather', timeoutMs: 9000,
      input: () => ({ city: new URLSearchParams(location.search).get('city') || cityFromTimeZone() }),
      run: async ({ city }, signal) => {
        const geo = await getJSON(`https://geocoding-api.open-meteo.com/v1/search?count=1&name=${encodeURIComponent(city)}`, signal);
        const place = geo?.results?.[0];
        if (!place) throw new Error(`I couldn't find a place called ${city}.`);
        const w = await getJSON(`https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}&current=temperature_2m,weather_code,wind_speed_10m`, signal);
        return { city: place.name, temp: w?.current?.temperature_2m, code: w?.current?.weather_code, wind: w?.current?.wind_speed_10m };
      },
      validate: (r) => typeof r.temp === 'number' && typeof r.code === 'number',
      done: (r) => {
        const kind = weatherKind(r.code);
        apply({ env: { weather: kind } });
        return [`In ${r.city} it's ${Math.round(r.temp)} degrees and ${WEATHER_WORDS[kind]}. ${kind === 'rain' ? 'Umbrella day!' : kind === 'clear' ? 'Good day for the forest.' : 'Hmm. Thinking weather.'}`, `${r.city}: ${Math.round(r.temp)}°C, ${WEATHER_WORDS[kind]}`];
      },
    },
    findout: {
      title: 'Find out', timeoutMs: 16000,
      input: (q) => { const s = String(q || '').trim(); if (s.length < 2) throw new Error('Tell me what to find out first.'); return { q: s.slice(0, 300) }; },
      run: async ({ q }, signal) => postJSON('/api/research', { q }, signal),
      validate: (r) => typeof r.answer === 'string' && r.answer.trim().length > 0,
      done: (r) => [`I asked ${r.provider}. It says: ${r.answer}`, `Answer from ${r.provider}`],
    },
    forest: {
      title: 'Trip', timeoutMs: 15000,
      input: () => null,
      run: async () => {
        if (st.asleep) throw new Error("He's asleep. Wake him first.");
        if (st.away) return { already: true };
        say('Forest Hills! The deer are waiting. Probably.');
        st.goalX = STATIONS.door; play('walk', 99);
        await until(() => Math.abs(st.x - STATIONS.door) < 3, 12000);
        st.away = true; st.place = 'forest'; st.x = 148; st.goalX = 148;
        apply({ away: true, place: 'forest', nib: { withPipo: true } });
        play('celebrate', 1.4);
        return { arrived: true };
      },
      validate: (r) => r.already || getState().place === 'forest',
      done: (r) => (r.already ? ["I'm already here! Look, a deer.", 'Already at Forest Hills'] : ['We made it! Nib, shh. Deer.', 'Arrived at Forest Hills']),
    },
  };

  async function runAction(id, raw) {
    const a = ACTIONS[id];
    if (!a) return false;
    if (st.busy) { toast(`Busy with ${st.busy}. One thing at a time.`, 'fail'); return true; }
    let input;
    try { input = a.input(raw); } catch (e) { say(e.message); toast(e.message, 'fail'); return true; }
    st.busy = a.title;
    ai('working', { title: a.title, detail: input?.q || input?.city || '…', status: 'running' });
    if (id !== 'forest') play('work', 99);
    const ctl = new AbortController();
    const timer = setTimeout(() => ctl.abort(new Error('timed out')), a.timeoutMs);
    try {
      const r = await a.run(input, ctl.signal);
      if (!a.validate(r)) throw new Error('The result looked wrong, so I threw it away.');
      const [line, short] = a.done(r);
      ai('success', { title: a.title, detail: short, status: 'ok' });
      play('celebrate', 1.2);
      say(line); toast('✓ ' + short, 'ok');
      record({ id, ok: true, detail: short });
    } catch (e) {
      const why = ctl.signal.aborted ? 'it took too long' : (e.message || 'something broke');
      ai('error', { title: a.title, detail: why, status: 'fail' });
      play('shrug', 2);
      say(`${a.title} didn't work: ${why}. Want me to try again?`);
      toast(`✕ ${a.title}: ${why}`, 'fail');
      record({ id, ok: false, detail: why });
    } finally {
      clearTimeout(timer); st.busy = null;
      setTimeout(() => { if (!st.busy) ai('idle'); }, 2600);
    }
    return true;
  }

  /* ---------- chat ---------- */
  async function chat(text) {
    const s = text.toLowerCase();
    if (st.asleep && !/wake/.test(s)) { st.asleep = false; apply({ hud: { asleep: false } }); }
    // same room-owned intents as the app's LocalBrain (actions are never left to the AI)
    if (/\b(suit off|armou?r off|take (off|of) (the |your )?(suit|armou?r)|remove (the |your )?(suit|armou?r))\b/.test(s)) { if (st.suit.mk) return runAction('suit'); return say("I'm not wearing it. This is just me."); }
    if (/\b(suit up|suit on|armou?r on|iron ?man)\b/.test(s)) { if (!st.suit.mk) return runAction('suit'); return say("It's already on. Very shiny."); }
    if (/\bweather\b/.test(s)) return runAction('weather');
    if (/^(find out|look up|search for)\b/.test(s)) return runAction('findout', text.replace(/^(find out|look up|search for)\s*/i, ''));
    if (/\b(forest|woods|deer)\b/.test(s) && /\b(go|let'?s|take me|visit)\b/.test(s)) return runAction('forest');
    if (/\b(come home|go home)\b/.test(s)) return comeHome();
    if (/\bdance\b/.test(s)) { play('dance', 5); return say('Music in my head. Go.'); }
    if (/\b(go to sleep|sleep|good ?night)\b/.test(s)) return sleep(true);
    if (/\bwake\b/.test(s)) return sleep(false);

    ai('thinking'); play('think', 99);
    try {
      const facts = [`the weather is ${getState().env?.weather || 'clear'}`, 'your pet is Nib, a small round beeping robot',
        st.suit.mk ? 'you are wearing your red and gold Mark One armor' : 'your Mark One armor is standing on its dock',
        st.away ? 'you are at Forest Hills with Nib: pine trees, deer, rabbits, robins' : 'you are at home in your room'];
      const r = await postJSON('/api/chat', { text, history: st.history.slice(-6), facts, mood: mood().toLowerCase(), activity: st.away ? 'exploring Forest Hills' : 'pottering around your room' }, AbortSignal.timeout(16000));
      if (!r.reply) throw new Error(r.error || 'no reply');
      st.history.push({ user: text, pipo: r.reply });
      ai('speaking'); play('talk', Math.min(6, 1.6 + r.reply.length * 0.05));
      say(r.reply);
      setTimeout(() => { if (!st.busy) ai('idle'); }, 2500);
    } catch (e) {
      // honest failure, not a shrug dressed as an answer
      ai('error', { title: 'Online brain', detail: String(e.message || e).slice(0, 80), status: 'fail' });
      play('shrug', 2);
      say("My online brain isn't answering right now, so I can't look that up. Ask me again in a minute?");
      setTimeout(() => ai('idle'), 3000);
    }
  }

  function sleep(on) {
    st.asleep = on;
    apply({ hud: { asleep: on } });
    if (on) { st.goalX = STATIONS.bed; say('Okay. Goodnight. Don’t let the screws bite.'); }
    else { play('celebrate', 0.8); say("I'm awake! I was always awake."); }
  }
  async function comeHome() {
    if (!st.away) return say("I'm already home. This is my room.");
    st.away = false; st.place = null; st.x = STATIONS.door; st.goalX = 160;
    apply({ away: false, place: null, nib: { withPipo: false } });
    play('walk', 99);
    say("I'm home! I saw a deer. It saw me. We had a moment.");
    toast('✓ Back home', 'ok');
  }

  /* ---------- the host interface the stage calls ---------- */
  const hostApi = {
    send: (t) => chat(t),
    action: (id) => {
      if (id === 'menu') return toast('Menu opens the app’s screens (map, journal, settings) on the phone.', '');
      if (id === 'mic') return toast('Voice uses the phone’s speech recognizer; not available in the browser preview.', '');
      if (id === 'sleep') return sleep(!st.asleep);
      if (id === 'dance') { play('dance', 5); return say('Watch this.'); }
      if (id === 'play') return toast('Games open as full screens in the app.', '');
      if (id === 'forest') return st.away ? comeHome() : runAction('forest');
      if (id.startsWith('findout:')) return runAction('findout', id.slice(8));
      runAction(id);
    },
    tap: (target) => {
      if (target === 'pipo') { if (st.asleep) return sleep(false); play('wave', 1.4); return say(['Hi!', 'Hehe. That tickles.', 'Beep.', 'Oh! Hi.'][Math.random() * 4 | 0]); }
      if (target === 'nib') return say(['Hiii!', 'Nib here!', 'Beep boop!'][Math.random() * 3 | 0], 'nib');
      if (target.startsWith('floor:') && !st.away && !st.asleep) { st.goalX = +target.slice(6); play('walk', 99); }
    },
    ready: () => {},
    get log() { return st.log; },
  };

  /* ---------- tick: a light stand-in for the app's behaviour engine ---------- */
  let last = performance.now();
  setInterval(() => {
    const now = performance.now();
    const dt = Math.min(0.1, (now - last) / 1000); last = now;
    st.t += dt; st.clockT += dt;
    if (st.anim !== 'idle' && st.clockT > st.animUntil) st.anim = 'idle';
    const dx = st.goalX - st.x;
    const walking = Math.abs(dx) > 1.5 && !st.asleep;
    if (walking) { st.x += Math.sign(dx) * Math.min(Math.abs(dx), 34 * dt); if (st.anim === 'idle') st.anim = 'walk'; }
    else if (st.anim === 'walk') st.anim = 'idle';
    if (st.asleep && Math.abs(dx) <= 1.5) st.anim = 'sleep';
    // his own little ideas when nobody's busy
    st.nextIdea -= dt;
    if (st.nextIdea <= 0 && !st.busy && !st.away && !st.asleep && st.anim === 'idle') {
      st.nextIdea = 9 + Math.random() * 10;
      const k = Object.keys(STATIONS).filter(s => s !== 'door');
      st.goalX = STATIONS[k[Math.random() * k.length | 0]];
    }
    const pose = { ...BASE, ...(ANIM[st.anim] || ANIM.idle)(st.t) };
    if (walking) pose.yaw = dx > 0 ? 1.2 : -1.2;
    // friendly at rest and on success, attentive while listening, thoughtful while working
    const expr = st.asleep ? 'CLOSED' : st.anim === 'think' ? 'CURIOUS' : st.anim === 'celebrate' || st.anim === 'wave' ? 'HAPPY' : st.anim === 'dance' ? 'EXCITED' : st.anim === 'shrug' ? 'WORRIED' : st.anim === 'work' ? 'FOCUSED' : st.anim === 'listen' ? 'CURIOUS' : 'CONTENT';
    apply({
      x: st.x, pose, expr, mood: mood(), sleepy: st.asleep, talking: st.anim === 'talk',
      look: [Math.sin(st.t * 0.4) * 0.4, 0],
      nib: { x: st.x - 22 + Math.sin(st.t * 0.3) * 6, anim: walking ? 'WALK' : st.asleep ? 'SLEEP' : 'IDLE', facing: walking ? (dx > 0 ? 1.4 : -1.4) : 0 },
      env: { hour: new Date().getHours() + new Date().getMinutes() / 60 },
    });
  }, 33);

  pushSuit();
  setTimeout(() => say('Hi. I’m Pipo. Tap me, or say something.'), 900);
  return hostApi;
}

/* ---------- helpers ---------- */
const wait = ms => new Promise(r => setTimeout(r, ms));
async function until(fn, ms) { const t0 = performance.now(); while (!fn()) { if (performance.now() - t0 > ms) throw new Error('he got stuck on the way'); await wait(50); } }
async function getJSON(url, signal) { const r = await fetch(url, { signal }); if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json(); }
async function postJSON(url, body, signal) {
  let r;
  try { r = await fetch(url, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body), signal }); }
  catch (e) { throw new Error(signal?.aborted ? 'it took too long' : 'the preview server is offline'); }
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(j.error || `HTTP ${r.status}`);
  return j;
}
const WEATHER_WORDS = { clear: 'clear', cloudy: 'cloudy', rain: 'rainy', fog: 'foggy', storm: 'stormy', wind: 'windy' };
function weatherKind(code) {
  if (code <= 1) return 'clear';
  if (code <= 3) return 'cloudy';
  if (code === 45 || code === 48) return 'fog';
  if (code >= 95) return 'storm';
  return 'rain';
}
function cityFromTimeZone() {
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Europe/London';
  return ({ 'Asia/Calcutta': 'Kolkata' })[tz] || tz.split('/').pop().replace(/_/g, ' ');
}
