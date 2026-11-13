// Local preview server for Pipo's 3D stage (app/src/main/assets/stage).
//   node tools/stage-preview/server.mjs        ->  http://127.0.0.1:5178/
// Serves the exact files the APK ships, plus /api/chat and /api/research, which call
// Gemini (then Groq) with the keys from local.properties. Keys never reach the browser.
// The persona mirrors app/src/main/java/com/pipo/robot/ai/ChatBrain.kt (PipoPrompt); keep them in step.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const STAGE = path.join(ROOT, 'app', 'src', 'main', 'assets', 'stage');
const PORT = +(process.env.PORT || 5178);

const props = Object.fromEntries((fs.existsSync(path.join(ROOT, 'local.properties')) ? fs.readFileSync(path.join(ROOT, 'local.properties'), 'utf8') : '')
  .split(/\r?\n/).filter(l => l.includes('=') && !l.startsWith('#')).map(l => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
const env = k => process.env[k] || props[k] || '';
const GEMINI_KEY = env('GEMINI_API_KEY'), GROQ_KEY = env('GROQ_API_KEY');
const GEMINI_MODEL = env('GEMINI_MODEL') || 'gemini-2.5-flash', GROQ_MODEL = env('GROQ_MODEL') || 'llama-3.3-70b-versatile';

const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.glb': 'model/gltf-binary', '.png': 'image/png', '.json': 'application/json', '.svg': 'image/svg+xml' };

function system({ facts = [], mood = 'curious', activity = 'in your room' }) {
  return [
    "You are Pipo, a tiny robot boy who lives inside the user's phone, in a cozy little room with a bed, a plant, a desk and a workbench.",
    'You are like a little kid, about four years old: curious, playful, easily excited, a bit stubborn, sometimes wrong, sometimes cheeky.',
    'Talk like a small child: short simple words, short sentences, big feelings, funny little ideas.',
    "You are NOT an assistant: never say things like 'How can I assist you', never mention being an AI or a language model.",
    'But you are small and clever: you have read everything on this phone, so you know lots of real things.',
    'When the user asks a real question (facts, maths, science, words, how-to, advice), give the CORRECT answer first, simply, in your own little voice, then you may add one tiny Pipo-ish remark.',
    "Never pretend not to know something ordinary. Only if it needs live information you can't have (today's news, scores, prices, the latest version of something) say you'd have to look it up, and that they can ask you to find out.",
    'Actually respond to what the user just said. Reply in 1 to 3 short sentences, plain text, no emojis, no lists, no stage directions.',
    `Current mood: ${mood}. Right now you are ${activity}.`,
    facts.length ? `True facts about your life right now: ${facts.join('; ')}.` : '',
    "These facts are your real life: USE them. Never invent places, things you own, purchases, or events in your own life that aren't listed.",
  ].filter(Boolean).join(' ');
}
const clean = s => (s || '').replace(/\*[^*]{1,40}\*/g, '').replace(/[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}\u{200D}]/gu, '').replace(/\s+/g, ' ').trim().replace(/^"|"$/g, '').slice(0, 400);
const assistanty = /\b(how (can|may) i (help|assist)|as an ai|language model|i('m| am) an ai|i('m| am) (an?|your) (ai )?assistant)\b/i;

const cool = {};
async function call(provider, sys, turns, temperature, maxTokens) {
  if ((cool[provider] || 0) > Date.now()) return null;
  let res;
  if (provider === 'gemini') {
    if (!GEMINI_KEY) return null;
    res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`, {
      method: 'POST', headers: { 'content-type': 'application/json', 'x-goog-api-key': GEMINI_KEY }, signal: AbortSignal.timeout(10000),
      body: JSON.stringify({ systemInstruction: { parts: [{ text: sys }] }, contents: turns.map(t => ({ role: t.role === 'assistant' ? 'model' : 'user', parts: [{ text: t.content }] })),
        generationConfig: { temperature, maxOutputTokens: maxTokens, thinkingConfig: { thinkingBudget: 0 } } }),
    });
  } else {
    if (!GROQ_KEY) return null;
    const body = { model: GROQ_MODEL, messages: [{ role: 'system', content: sys }, ...turns], temperature, max_completion_tokens: 400 };
    if (GROQ_MODEL.includes('gpt-oss')) body.reasoning_effort = 'low';
    res = await fetch('https://api.groq.com/openai/v1/chat/completions', {
      method: 'POST', headers: { 'content-type': 'application/json', authorization: `Bearer ${GROQ_KEY}` }, signal: AbortSignal.timeout(10000), body: JSON.stringify(body),
    });
  }
  if (!res.ok) {
    console.warn(`[${provider}] HTTP ${res.status}`);   // status only, never the conversation
    if (res.status === 429 || res.status >= 500) cool[provider] = Date.now() + (res.status === 429 ? 60000 : 15000);
    return null;
  }
  const j = await res.json();
  const text = provider === 'gemini' ? j.candidates?.[0]?.content?.parts?.map(p => p.text || '').join('') : j.choices?.[0]?.message?.content;
  return clean(text) || null;
}

async function answer(sys, turns, temperature, maxTokens, guard) {
  for (const p of ['gemini', 'groq']) {
    try {
      const t0 = Date.now();
      const r = await call(p, sys, turns, temperature, maxTokens);
      console.log(`[${p}] ${r ? 'answered' : 'skipped/failed'} in ${Date.now() - t0}ms`);
      if (r && (!guard || !assistanty.test(r))) return { reply: r, provider: p === 'gemini' ? 'Gemini' : 'my AI friend' };
    } catch (e) { console.warn(`[${p}] ${e.name}`); }
  }
  return null;
}

async function body(req) {
  let s = ''; for await (const c of req) { s += c; if (s.length > 20000) throw new Error('too big'); }
  return JSON.parse(s || '{}');
}
const send = (res, code, obj) => { res.writeHead(code, { 'content-type': 'application/json' }); res.end(JSON.stringify(obj)); };

http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://x');
    if (req.method === 'POST' && url.pathname === '/api/chat') {
      const b = await body(req);
      const text = String(b.text || '').slice(0, 500);
      if (!text) return send(res, 400, { error: 'empty message' });
      const turns = (Array.isArray(b.history) ? b.history.slice(-6) : []).flatMap(t => [{ role: 'user', content: String(t.user).slice(0, 300) }, { role: 'assistant', content: String(t.pipo).slice(0, 400) }]);
      const r = await answer(system(b), [...turns, { role: 'user', content: text }], 0.9, 200, true);
      return r ? send(res, 200, r) : send(res, 503, { error: GEMINI_KEY || GROQ_KEY ? 'both online brains failed' : 'no AI keys in local.properties' });
    }
    if (req.method === 'POST' && url.pathname === '/api/research') {
      const b = await body(req);
      const q = String(b.q || '').slice(0, 300);
      if (q.length < 2) return send(res, 400, { error: 'nothing to find out' });
      const r = await answer('Answer the question accurately and helpfully in at most 3 short sentences. Plain text, no lists, no markdown.', [{ role: 'user', content: q }], 0.3, 220, false);
      return r ? send(res, 200, { answer: r.reply.replace(/\*/g, ''), provider: r.provider }) : send(res, 503, { error: 'the internet isn’t answering' });
    }
    // static: only files inside the stage folder
    const rel = decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname);
    const file = path.normalize(path.join(STAGE, rel));
    if (!file.startsWith(STAGE) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); return res.end('not found'); }
    res.writeHead(200, { 'content-type': TYPES[path.extname(file)] || 'application/octet-stream', 'cache-control': 'no-store' });
    fs.createReadStream(file).pipe(res);
  } catch (e) {
    console.error(e);
    send(res, 500, { error: 'preview server error' });
  }
}).listen(PORT, '127.0.0.1', () => console.log(`Pipo stage preview: http://127.0.0.1:${PORT}/  (gemini ${GEMINI_KEY ? 'on' : 'off'}, groq ${GROQ_KEY ? 'on' : 'off'})`));
