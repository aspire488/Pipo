# Pipo AI proxy

Keeps your Gemini and Groq keys **out of the app**. The released app only knows this
proxy's address; the proxy adds the keys and forwards Pipo's requests.

## Deploy (Cloudflare Workers, free tier)

```sh
cd server
npx wrangler login
npx wrangler secret put GEMINI_API_KEY     # paste your Gemini key
npx wrangler secret put GROQ_API_KEY       # paste your Groq key
npx wrangler secret put APP_TOKEN          # any long random string
npx wrangler deploy                        # prints https://pipo-ai-proxy.<you>.workers.dev
```

## Point the app at it

In `local.properties`:

```
PIPO_PROXY_URL=https://pipo-ai-proxy.<you>.workers.dev
PIPO_PROXY_TOKEN=<the same APP_TOKEN>
```

Then build the release. With `PIPO_PROXY_URL` set, **the release APK contains no provider
keys** (they're blanked at build time). Debug builds still use your local keys if present.

## What it allows

- `POST /gemini/<model>:generateContent` for Pipo's Gemini models only
- `POST /groq/chat/completions` for Pipo's Groq models only, max 600 output tokens
- bodies up to 1.5 MB (a downscaled photo for "Pipo, look!" is ~100 KB)
- per-install limits: 20 requests/minute, 400/day (in-memory, best effort). For a hard
  limit, enable the `[[ratelimits]]` binding in `wrangler.toml`.

`APP_TOKEN` ships inside the app, so it isn't a true secret. It only keeps casual scrapers
out. The real protection is that your provider keys never leave Cloudflare, and that you can
rotate them (or the token) at any time without shipping a new app.

## Privacy

Requests contain what Pipo sends to the AI: the chat text, a summary of Pipo's state, and, when
you use "Pipo, look!", one downscaled photo. The proxy doesn't store anything. Cloudflare,
Google and Groq process the requests under their own terms.
