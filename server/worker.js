// Pipo's AI proxy (Cloudflare Worker).
//
// The app never holds the Gemini/Groq keys: it calls this Worker, which adds the keys (stored as
// Worker secrets) and forwards to the provider. Only the two endpoints Pipo uses are allowed, only
// with the models he uses, and each phone install is rate-limited.
//
// Routes:
//   POST /gemini/<model>:generateContent   -> Google Generative Language API
//   POST /groq/chat/completions            -> Groq (OpenAI-compatible)

const GEMINI_MODELS = new Set(["gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash"]);
const GROQ_MODELS = new Set(["llama-3.3-70b-versatile", "llama-3.1-8b-instant", "meta-llama/llama-4-scout-17b-16e-instruct", "openai/gpt-oss-20b"]);
const MAX_BODY = 1_500_000;          // a downscaled photo is ~100 KB; chats are tiny
const PER_MINUTE = 20;               // requests per install per minute
const PER_DAY = 400;                 // requests per install per day (best effort, per Worker isolate)

// Best-effort in-memory counters (per isolate). The primary identity is the server-observed
// Cloudflare client IP; install IDs are only an additional signal because client headers are spoofable.
// For stronger protection, bind Cloudflare Rate Limiting and use provider-side quota/billing alerts.
const minute = new Map();
const day = new Map();

function tooMany(id) {
  const now = Date.now();
  const m = minute.get(id);
  if (!m || now - m.t > 60_000) minute.set(id, { t: now, n: 1 }); else if (++m.n > PER_MINUTE) return true;
  const dk = Math.floor(now / 86_400_000);
  const d = day.get(id);
  if (!d || d.k !== dk) day.set(id, { k: dk, n: 1 }); else if (++d.n > PER_DAY) return true;
  if (minute.size > 50_000) minute.clear();
  if (day.size > 50_000) day.clear();
  return false;
}

const json = (status, obj) => new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json" } });

export default {
  async fetch(req, env) {
    if (req.method !== "POST") return json(405, { error: "POST only" });
    // Never trust client-supplied tokens or install IDs as authentication. They can be extracted/spoofed.
    // Cloudflare provides the client IP at the edge; use it as the primary abuse-control key.
    const ip = req.headers.get("CF-Connecting-IP") || "unknown";
    const install = (req.headers.get("x-pipo-install") || "").slice(0, 64);
    const identity = /^[0-9a-f-]{20,64}$/.test(install) ? `${ip}:${install}` : ip;
    if (env.RATE_LIMITER) {
      const ipLimit = await env.RATE_LIMITER.limit({ key: ip });
      if (!ipLimit.success) return json(429, { error: "slow down" });
    }
    if (tooMany(ip) || tooMany(identity)) return json(429, { error: "slow down" });

    const len = Number(req.headers.get("content-length") || 0);
    if (len > MAX_BODY) return json(413, { error: "too big" });
    const body = await req.text();
    if (body.length > MAX_BODY) return json(413, { error: "too big" });

    const url = new URL(req.url);
    const gem = url.pathname.match(/^\/gemini\/([a-z0-9.\-]+):generateContent$/);
    if (gem) {
      if (!GEMINI_MODELS.has(gem[1])) return json(400, { error: "model not allowed" });
      const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${gem[1]}:generateContent`, {
        method: "POST", headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY }, body,
      });
      return new Response(r.body, { status: r.status, headers: { "content-type": "application/json" } });
    }
    if (url.pathname === "/groq/chat/completions") {
      let parsed;
      try { parsed = JSON.parse(body); } catch { return json(400, { error: "bad json" }); }
      if (!GROQ_MODELS.has(parsed.model)) return json(400, { error: "model not allowed" });
      if ((parsed.max_completion_tokens || parsed.max_tokens || 0) > 600) return json(400, { error: "too many tokens" });
      const r = await fetch("https://api.groq.com/openai/v1/chat/completions", {
        method: "POST", headers: { "content-type": "application/json", "authorization": `Bearer ${env.GROQ_API_KEY}` }, body,
      });
      return new Response(r.body, { status: r.status, headers: { "content-type": "application/json" } });
    }
    return json(404, { error: "not found" });
  },
};
