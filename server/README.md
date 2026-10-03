# Pipo AI proxy

**Security boundary:** Gemini and Groq provider keys stay out of the app. The proxy adds the provider credentials and forwards Pipo's requests.

## Deploy (Cloudflare Workers, free tier)

```sh
cd server
npx wrangler login
npx wrangler secret put GEMINI_API_KEY     # paste your Gemini key
npx wrangler secret put GROQ_API_KEY       # paste your Groq key
npx wrangler deploy                        # prints https://pipo-ai-proxy.<you>.workers.dev
```

## Point the app at it

In `local.properties`:

```
PIPO_PROXY_URL=https://pipo-ai-proxy.<you>.workers.dev
```

Then build the release. With `PIPO_PROXY_URL` set, **the release APK contains no provider
keys** (they're blanked at build time). Debug builds still use your local keys if present.

## What it allows

- `POST /gemini/<model>:generateContent` for Pipo's Gemini models only
- `POST /groq/chat/completions` for Pipo's Groq models only, max 600 output tokens
- bodies up to 1.5 MB (a downscaled photo for "Pipo, look!" is ~100 KB)
- best-effort per-install limits: 20 requests/minute, 400/day (in-memory). These limits are **abuse controls, not authentication**.
- for meaningful quota protection, enforce limits server-side using a Cloudflare binding, authenticated server-issued session identifiers, and/or Cloudflare rate limiting/WAF rules. Never rely on a client-generated install ID or embedded token as proof of identity.

Do not put provider credentials in the APK. Assume every client-visible token can be copied and replayed. If the proxy is exposed publicly, configure server-side rate limiting and billing/quota alerts before distributing the APK widely.

## Privacy

Requests contain what Pipo sends to the AI: the chat text, a summary of Pipo's state, and, when
you use "Pipo, look!", one downscaled photo. The proxy doesn't store anything. Cloudflare,
Google and Groq process the requests under their own terms.
