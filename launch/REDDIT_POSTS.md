# Pipo — ready-to-publish Reddit drafts

Four different posts for four different rooms. They share facts, not sentences.
**Nothing here has been posted.** Read the note at the top of each draft before you submit.

Facts used below are the ones that are true of the **public v1.0 APK** (verified against
`dist\Pipo.apk`), not of unreleased work on `main`.

---

## 1. r/SideProject — primary launch post

- **Flair:** none required.
- **Attach:** `dist\Pipo-Intro.mp4` (26 s, real app recording, 10 MB) as a **native video upload**.
- **When:** Tue–Thu, 08:00–11:00 US Eastern.
- **Title format:** the sidebar asks for "Project name - short description".

**Title**
```
Pipo - a little robot who lives in your phone and gets on with his own day
```

**Body**
```
I've been building a small robot called Pipo. He isn't an assistant and there's no productivity
angle - the whole point is that you open the app and find out what he's doing.

He has ten traits rolled at first launch, twelve moods, memories that decay at different rates, and
a scoring engine that picks his next activity from about twenty options. Sometimes he builds
something at his workbench, sometimes he lies on the floor staring at the ceiling, and sometimes
he's not there at all: he's gone to the hardware shop because the flying machine needs a motor, and
there's a note on the door. His pet Nib stays behind and sleeps in a cardboard box.

The part I'm happiest with is that everything is drawn in code. No 3D models, no sprite sheets, no
image assets - Pipo, his room, the weather in the window and the parallax outside are all procedural
Compose Canvas drawing, with a small 2.5D projection so he can turn through front, three-quarter,
profile and back. It's a 26 MB APK with no game engine in it.

Honest about the limits:
- It's a sideload from GitHub, not a Play Store listing. Play Protect will ask you to confirm the
  install, because that's what it does for any app that isn't distributed by Play.
- Chat is offline in the build you can download: he answers in his own words from a rule-based
  brain. Online AI wording needs a proxy you host yourself (the code is in the repo) - it is not
  in the download.
- The only network calls in the APK are optional real weather (approximate city from your IP) and
  the links he opens for you. No account, no analytics, no ads, one JSON save file on the device.

There's also a browser version of his room in 3D that needs no install:
https://aspire488.github.io/Pipo/ (and the older 2D site at https://aspire488.github.io/Pipo/classic/)

What I'd genuinely like to know: after 20 seconds of the video, is it clear what the app *is*?
That's the thing I keep failing at when I try to describe it.

Release and APK: https://github.com/aspire488/Pipo/releases/tag/v1.0
Source (MIT): https://github.com/aspire488/Pipo
```

**Why this shape:** story first, one concrete ask, links last, no adjectives doing the work of facts.

---

## 2. r/droidappshowcase — templated showcase

- **Flair:** **Promo** (use *Feedback* instead if you want critique more than installs).
- **Attach:** `dist\Pipo-Intro-Vertical.mp4` (26 s, vertical, 6.4 MB) as a native video upload —
  this sub is overwhelmingly portrait media.
- **Frequency:** one post per app per 7 days. Account must be >24 h old with 2+ karma.
- **Template is mandatory** (Rule 4); this draft follows the mods' own example.

**Title**
```
Pipo - a robot companion who lives his own life - free
```

**Body**
```
App Name: Pipo

Link (signed APK, Android 8.0+, ~26 MB):
https://github.com/aspire488/Pipo/releases/download/v1.0/Pipo.apk

What it does:
Pipo is a small robot who lives in a room inside your phone. He isn't an assistant - there are no
tasks, no streaks and no score. He has his own traits, moods, memories and unfinished projects, and
he decides what to do next. Open the app and you find out: he might be welding something at his
workbench, chasing a moth around the desk lamp, pretending to be asleep, or gone to the hardware
shop with a note left on the door. Time you spend away is replayed through the same engine, so
something has usually changed when you come back.

Key Features:
- Autonomous life: 20+ activities scored from traits, mood, time of day and what he did recently
- Nib, his small robot pet, with its own moods, bond and habit of stealing shiny things
- Trips to 13 places, his own weather, money from odd jobs, food he cooks (and sometimes burns)
- Projects that need parts, can fail, and get retried with experience carried over
- 8 games, a journal written in his own hand, and a slow background mystery with no quest markers
- Everything drawn procedurally in Compose Canvas - no 3D models, no sprite sheets, no game engine
- Local-first: one JSON save file, no account, no analytics, no ads

Pricing: free. No ads, no in-app purchases, no subscription. Source is MIT licensed.

Privacy: no account and no telemetry. Optional network use is real weather (approximate city from
your IP, via ipwho.is and Open-Meteo) and the links he opens for you. Notification access, device
admin (lock only) and microphone are optional, off by default, and each is granted in Android's own
settings. Nothing about your notifications is stored or sent.

Note: this is not on the Play Store yet - it's a signed APK from GitHub, so Play Protect will ask
you to confirm the install. That prompt is expected for any app distributed outside Play.

Feedback I'm looking for: does he read as a character in the first minute, or does he still look
like a toy with animations? Honest answers are more useful to me than nice ones.
```

---

## 3. r/developersIndia — engineering post ("I Made This")

- **Flair:** **I Made This** (required by the current mod policy).
- **Attach:** optional. A single screenshot or the 26 s clip is enough; this sub reads text.
- **Hard rule from the 2026-08-02 mod announcement:** the post must be about the engineering. Posts
  "primarily meant to drive traffic to a website/application (asking people to install your app …)"
  are removed as self-promotion. So: **no install CTA, one link line at the end.**
- Write it in your own words. AI-flavoured text is explicitly removed.

**Title**
```
I Made This: a deterministic life-simulation engine for a character that lives on your phone
```

**Body**
```
I built Pipo, an Android app about a small robot who lives in a room on your phone and runs his own
day. The interesting part for this sub isn't the character, it's the two engineering problems behind
it: making a simulation that is deterministic and cheap enough to run entirely on-device, and drawing
a 2.5D character with no 3D engine and no art assets.

The problem I was solving
A "virtual pet" usually means timers and meters. I wanted behaviour that is defensible: if you ask
why he did something, there's a reason, and if you rewind the same state and seed you get the same
day back. That requirement shaped everything else.

Stack
Kotlin, Jetpack Compose (no game engine, no OpenGL, no Unity), WorkManager for the hourly tick,
kotlinx.serialization for a single JSON save, Vosk for the optional offline wake word, Android TTS
for the voice.

What was hard

1. Determinism with personality. Traits are rolled once from a seed; mood is derived from continuous
   vectors (energy, boredom, loneliness, irritation, excitement, curiosity, happiness, affection)
   rather than set directly. Each tick scores ~20 activities from traits x mood x environment x
   recent history, then picks among everything within reach of the best idea, with a small pull
   toward things not done lately. The randomness is a seeded PRNG threaded through the engine, so
   the same inputs produce the same choices. Unit tests simulate whole days and assert that at least
   14 activity kinds appear and nothing exceeds 30% of the day.

2. Catching up on time away without a server. Nothing runs in the background except one hourly
   worker. When the app opens, the elapsed time is replayed through the same engine in coarse steps,
   which is why the room can be empty when you open it: he left for the hardware shop twenty minutes
   ago and the replay decided that. The save is written atomically (tmp + rename) under a single
   lock, and a corrupt file is set aside instead of crashing the app.

3. Drawing a 3D-feeling character in Compose Canvas. Every body part has a position in a small local
   3D space and is projected through his current yaw, then depth-sorted, so the far ear and arm draw
   behind the body and shade darker. He turns through front, three-quarter, profile and back; the
   face screen foreshortens and slides; the head has its own yaw so it leads the body. Lighting picks
   the strongest of the room's real light sources (window by day, desk lamp at night, charger glow)
   and derives a key light, rim light and contact shadow from it. The environment gets a perspective
   floor with converging seams and parallax layers that respond to device tilt.

4. Performance. Compose's Color lerp (Oklab) was one of the hottest paths on a real phone, so colour
   blending uses a small packed-sRGB lerp instead. That single change was worth more than any
   micro-optimisation in the painters.

5. Voice. Android has no child voices. Raising a TTS voice's pitch alone sounds like a chipmunk
   adult, because pitch and formants move together in a real child's vocal tract. So the line is
   synthesised slowly with raised pitch, written to a file, and played back at 1.45x through
   AudioTrack, which scales pitch and formants together. Rendering is ~70-200 ms per line on a
   Galaxy S23.

Trade-offs I accepted
- No engine and no 3D assets: everything is code, so there is nothing to import, but every visual
  effect is my problem.
- A single JSON save instead of a database: trivial to inspect and back up, but every schema change
  needs a migration (schema 1 -> 2 copies the old file aside first).
- Offline-first AI: the rule-based brain owns every state change, and an LLM (if you run your own
  proxy) only words the reply. That keeps behaviour testable, at the cost of a second system to
  maintain.

What I'd do differently
- I'd design the save schema with explicit versioned migrations from day one instead of retrofitting
  them.
- I'd build the test harness that simulates whole days before building the behaviour, not after. It
  found more design problems than any amount of manual play.

It's MIT licensed and the code is here if any of it is useful to you:
https://github.com/aspire488/Pipo
```

---

## 4. r/androiddev — comment for the monthly showcase thread only

- **Do not make this a post.** Rule 2: "Sharing applications or recruiting testers is not allowed."
- The only sanctioned place is the mod-run **"Interesting Android Apps: \<Month\> \<Year\> Showcase"**
  thread. The series ran monthly through July 2026 and has not appeared since; a 2026-10-28 thread
  asks whether it is gone for good. **Check the sub before using this.**
- Mods ask you to state whether the app is free, paid or subscription-based. Keep it to a few lines.
- No emoji, no gushing — Rule 5 removes LLM-flavoured text.

**Comment**
```
Pipo - a character-first Android app, free, no ads or IAP, MIT source.

It's a small robot who lives in a procedurally drawn room and runs his own day: traits and moods
scored into 20+ activities, a pet with its own bond level, trips to 13 places, a journal, and time
away replayed through the same engine so something has changed when you come back. No account, no
analytics, one JSON save.

Technically the interesting bit is that there is no engine and no art: the character and room are
procedural Compose Canvas with a small 2.5D projection and depth sorting, which is how he turns
through front/profile/back without a single 3D model.

One honest caveat: it's sideloaded from GitHub, so Play Protect asks for confirmation on install,
and chat in the published APK is offline (online wording needs a proxy you host).

https://github.com/aspire488/Pipo
```

---

## 5. Rules for every reply you write afterwards

- Answer the question that was asked. If someone says "this is just an AI chatbot", do not argue the
  category — describe one concrete thing he did that a chatbot does not do, and move on.
- Never repeat the download link unprompted. One link in the post; after that, only if someone asks
  where to get it.
- If someone reports a bug, say what you will change. If you fix it, reply with the commit.
- Do not ask for upvotes, stars, follows or installs, in any wording.
- Do not post the same reply text in several threads; people check profiles.
