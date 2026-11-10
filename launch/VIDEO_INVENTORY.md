# Pipo — existing launch video inventory

**Scope:** every video already stored inside `D:\pipo\Pipo`, found by recursive search for
`*.mp4 / *.webm / *.mov / *.mkv / *.avi / *.gif` (excluding `node_modules`, `.git`, `build/intermediates`).
**Date:** 2026-11-10.

> **Nothing was created, generated, cut, assembled, re-encoded or modified.** No video file was written,
> renamed or moved. The only files produced during inspection were still frames written to a scratch
> folder outside the repo (`D:\tessera\.tmp\opencode\`), used to describe what each video shows.
> Every hash below can be re-checked to prove the originals are untouched.

---

## 1. All video files found

| # | Path | Length | Resolution | FPS | Size | Codecs | SHA-256 |
|---|---|---|---|---|---|---|---|
| 1 | `D:\pipo\Pipo\dist\pipo-promo.mp4` | 97.9 s | 1920×1080 | 30 | 82.04 MB | H.264 / AAC 48 kHz stereo | `7f3e27aeba620c76…` |
| 2 | `D:\pipo\Pipo\dist\pipo-promo-vertical.mp4` | 97.9 s | 1080×1920 | 30 | 75.92 MB | H.264 / AAC 48 kHz stereo | `57ecb6de80f61588…` |
| 3 | `D:\pipo\Pipo\dist\pipo-39s.mp4` | 39.0 s | 1920×1080 | 30 | 61.64 MB | H.264 / AAC 48 kHz stereo (101 kbps) | `fdc869612447b2ef…` |
| 4 | `D:\pipo\Pipo\dist\pipo-39s-bgm.mp4` | 39.0 s | 1920×1080 | 30 | 61.98 MB | H.264 / AAC 48 kHz stereo (175 kbps) | `adc439ca15362b55…` |
| 5 | `D:\pipo\Pipo\dist\Pipo-Intro.mp4` | 26.0 s | 1920×1080 | 30 | 10.07 MB | H.264 / AAC 44.1 kHz stereo | `b86ae9bb09dd7bdb…` |
| 6 | `D:\pipo\Pipo\dist\Pipo-Intro-Vertical.mp4` | 26.0 s | 1080×1920 | 30 | 6.44 MB | H.264 / AAC 44.1 kHz stereo | `f437935a274d8a7c…` |
| 7 | `D:\pipo\Pipo\Pipo-Intro.mp4` | 26.0 s | 1920×1080 | 30 | 10.07 MB | H.264 / AAC 44.1 kHz stereo | `b86ae9bb09dd7bdb…` |
| 8 | `D:\pipo\Pipo\Pipo-Intro-Vertical.mp4` | 26.0 s | 1080×1920 | 30 | 6.44 MB | H.264 / AAC 44.1 kHz stereo | `f437935a274d8a7c…` |
| 9 | `D:\pipo\Pipo\docs\preview\walkthrough.mp4` | 15.5 s | 412×916 | 24 | 0.96 MB | H.264, **no audio** | `55c52364d118b76d…` |

Full hashes (in order): `7f3e27aeba620c760eb62958f30874614162ba886fa9f5e407427398c39adc7c`,
`57ecb6de80f6158804a1919c72ec02c55aaa1a17a4b24729de9955d275f82c0d`,
`fdc869612447b2efedf39363e263d6da24ea99367a0d708a1c930b24763996e8`,
`adc439ca15362b55726fbe87e3812dad16c96669798cc979152315259a5febd4`,
`b86ae9bb09dd7bdbe5372e1301ac2b0e060c0a1cf9d4e01c9a53cf6fae8a4669`,
`f437935a274d8a7c28f112283d0439001b55caf2ff265bfc38a2d4009d557a92`,
`55c52364d118b76df3470fe58e829c3ce01e7334301b33a2ac27718c5cc79870`.

### Duplicates (not extra assets)

- `Pipo-Intro.mp4` (repo root) is **byte-identical** to `dist\Pipo-Intro.mp4` — same hash.
- `Pipo-Intro-Vertical.mp4` (repo root) is **byte-identical** to `dist\Pipo-Intro-Vertical.mp4` — same hash.
  These two root copies are the ones committed to git and linked from `README.md`.
- `pipo-39s.mp4` and `pipo-39s-bgm.mp4` are the **same picture**, two audio mixes: a frame grabbed at
  00:12 from each is bit-identical. `-bgm` has the music bed (175 kbps AAC), the plain one does not
  (101 kbps AAC).

So there are **7 distinct videos**: the 98 s film (landscape + vertical), a 39 s cut (two audio mixes),
the 26 s app intro (landscape + vertical), and one old docs clip.

---

## 2. What each video actually shows

Described from sampled frames and the project's own production notes (`promo/README.md`,
`promo/PRODUCTION_PLAN.md`), not from guesswork.

### `dist\pipo-promo.mp4` — the 98 s film, "Come find him" (landscape)
Eight scenes, painted every frame from TypeScript ports of the app's own Compose painters — it is
**not** screen capture: Nib alone in the room; Pipo at his workbench, head turning; the room at night
with the mirror; him at the window; then the world outside — the house, the path, the town.
Original score, ambience, and Pipo's real device-rendered toddler voice lines. Ends on the room
becoming the round window of his house, the house becoming the world.

### `dist\pipo-promo-vertical.mp4` — the same film, 1080×1920
Same edit and soundtrack, reframed for phones. This is the version for Reels / Shorts / TikTok.

### `dist\pipo-39s-bgm.mp4` — 39 s cut with music (landscape)
A tighter cut of the same film: the empty room with Nib, Pipo standing in the middle of the room,
the workbench, the night scene with the blue-lit mirror, and the morning window with the plant.
Music bed present. Best "short" for a feed that autoplays with sound.

### `dist\pipo-39s.mp4` — same 39 s cut, no music bed
Identical picture; ambience/dialogue only. Use when a platform mutes or when a music bed would fight
the app's own sound design.

### `dist\Pipo-Intro.mp4` — 26 s, the **real app** (landscape)
An actual screen recording of the Android app, not a render: Pipo in his room, speech bubbles,
the real chat bar ("Say something to Pipo…"), real typed input ("hello pipo") and his reply
("Oh, hi Joel! I was just here. Being here."), his voice, the mic button, his face reacting.
This is the only video that proves what the APK looks like and how it behaves.

### `dist\Pipo-Intro-Vertical.mp4` — same 26 s app recording, 1080×1920
Same footage, vertical.

### `docs\preview\walkthrough.mp4` — 15.5 s, 412×916, silent, **outdated**
An old screen recording from before the current character/room design: a different TV-headed robot
("Mark One. Online."), an older HUD ("Suit off (Mk I)", "Find out", "Weather"), and a low-poly forest.
Low resolution, no audio, UI that no longer matches the app.
**Do not post this publicly** — it would misrepresent the current build. Keep it as a docs asset.

---

## 3. Ranking for Reddit and social

Ranked on: does it explain the product in the first 3 seconds, is the format native to the platform,
and does it survive being watched with sound off.

| Rank | File | Why | Best use |
|---|---|---|---|
| **1** | `dist\Pipo-Intro.mp4` | Real app, real chat, real voice, 26 s, 10 MB. A viewer sees the product immediately and cannot mistake it for a render or a chatbot mock-up. | Reddit native upload (r/SideProject, r/droidappshowcase), X/Twitter, LinkedIn, GitHub README |
| **2** | `dist\pipo-39s-bgm.mp4` | 39 s is the sweet spot for a feed; music carries it when muted viewers unmute; strong "what is this?" opening. | Reddit native upload, YouTube (as a short landscape clip), Discord, WhatsApp status |
| **3** | `dist\pipo-promo.mp4` | The full story, the best craft, the one that makes people feel something. 98 s is long for a launch post but excellent as the second thing they click. | YouTube, the release notes, a comment reply ("here's the 90-second film"), the website |
| **4** | `dist\Pipo-Intro-Vertical.mp4` | Native vertical for phones; same proof as #1. | Instagram Reels, YouTube Shorts, Reddit mobile (secondary) |
| **5** | `dist\pipo-promo-vertical.mp4` | The full film, vertical. Best craft in the format social actually rewards. | Instagram Reels, YouTube Shorts, TikTok |
| **6** | `dist\pipo-39s.mp4` | Same picture as #2 without the music. Only useful if a music bed is a problem. | Anywhere #2 is blocked/muted for audio reasons |
| **—** | `docs\preview\walkthrough.mp4` | Outdated UI, 412×916, silent. | Documentation only, never a launch post |

**If only one video may be used:** `dist\Pipo-Intro.mp4`. It is the honest one — it shows the app
people can actually download today.

---

## 4. Practical constraints for posting (checked, not assumed)

- **`dist/` is git-ignored** (`dist/`, `*.mp4` in `.gitignore`). Only the two root intro videos are
  tracked in git. Nothing in `dist/` has a public URL, so those files can only be used by **uploading
  them natively** to Reddit/Instagram/YouTube from this machine.
- `https://aspire488.github.io/Pipo/Pipo-Intro.mp4` returns **404** — GitHub Pages does not serve the
  repo-root videos. Do not put that URL in a post.
- `https://raw.githubusercontent.com/aspire488/Pipo/main/Pipo-Intro.mp4` does resolve (HTTP 206,
  `application/octet-stream`), but it downloads a file instead of playing inline, and several
  subreddits treat raw file hosts as low-reputation links. Prefer a native upload; use the raw URL
  only as a fallback in a comment.
- Sizes: 82 MB and 76 MB are within Reddit's upload limit but are a real cost on mobile data. The
  10 MB intro and the 62 MB 39 s cut are the friendlier choices; the 26 s intro is the only one that is
  cheap to watch on a phone.
- **Do not re-encode to shrink them** — that is explicitly out of scope for this task. If size ever
  matters, that is a separate, approved decision.
- Aspect: Reddit's player handles both, but landscape (16:9) reads better in a desktop feed and vertical
  reads better on mobile. r/droidappshowcase posts are overwhelmingly portrait screenshots/GIFs, so
  `Pipo-Intro-Vertical.mp4` fits the house style there.
