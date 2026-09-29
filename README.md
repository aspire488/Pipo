# Pipo 🤖

_A tiny robot who lives inside your phone._

Pipo is a **character-first Android experience**: a small, curious, slightly mischievous robot who has his own moods, memory, habits and hobbies, and who happens to live in a little workshop inside your phone.

He is not a generic assistant, not a productivity app, and not a chatbot skin. Nobody asks Pipo for a to-do list. He is the one with things to do.

The whole product is one sentence:

> "I wonder what Pipo is doing."

Open the app and you find out. He might be asleep with the lamp off, halfway through building something questionable, putting a tiny hat on your plant, or standing near the front of the room because he heard you come back.

Everything runs on your device. There is no account, no server, and no analytics — just a robot and a JSON file.

## What Pipo is

A simulated character with a persistent state that survives restarts and keeps evolving:

- **A body in a room.** He walks between stations (bed, charger, workbench, arcade, window, plant, rug), sits, sleeps, dances, experiments, and stares thoughtfully out the window. Everything you see is drawn in code — no sprite sheets, no bitmaps.
- **A mind with opinions.** Traits, mood, memories and an active project decide what he does next, what he says, and how he says it.
- **A life that continues without you.** Nothing runs continuously in the background, but whenever the app reopens (or the hourly worker fires), Pipo's elapsed time is replayed in coarse steps through the same behavior engine. He may have found something, finished a project, or drawn you a picture while you were gone.
- **A relationship, not a service.** Relationship grows slowly with shared time and never decreases because you were absent. He can be lonely, but he never guilt-trips you for it.

There is no objective, no streak to protect, and no score for the user. The reward is watching him.

## Pipo's personality and moods

**Ten traits** — curiosity, playfulness, mischief, affection, confidence, sociability, patience, laziness, adventurousness, stubbornness — are rolled at first run, so no two Pipos are identical. They evolve in tiny bounded increments from what he actually does; he never flips into a caricature. "Shy" isn't stored anywhere: it emerges from low sociability plus low confidence.

**Twelve moods** — happy, excited, curious, sleepy, bored, grumpy, lonely, nervous, proud, embarrassed, mischievous, relaxed — are derived from continuous mood vectors (energy, boredom, loneliness, irritation, excitement, curiosity, happiness, affection). Short-lived **transient moods** override the derived one: he stays proud for a minute and a half after finishing a project, and embarrassed for forty seconds after a failure.

Mood is not decoration. It multiplies which activities he considers, which lines he picks, the voice a notification is written in, and the pitch of his TTS.

## His autonomous little life

`BehaviorEngine` scores eighteen candidate activities as a pure function of traits × mood × environment (hour of day, charging, music playing, headphones, you being present), then picks with controlled randomness among the top few, respecting cooldowns so he doesn't loop.

He can: sleep, rest, charge, explore, play with a toy, play arcade, experiment, build, examine his collection, rearrange things (and occasionally prank you), read, think, work at the computer, inspect the plant, dance (music makes him dance a lot), prepare a surprise, come looking for you, or simply do nothing.

The loop runs live while the app is open (with time scaled so you can actually watch him get tired) and replays in 40-minute steps when he catches up — capped at three days, so a phone left off for a month doesn't produce a novel.

## Memory and journal

`Chronicle` is his hippocampus:

- **Memories** are typed (`USER_FACT`, `JOKE`, `DISCOVERY`, `GAME`, `PROJECT`, `MOMENT`, `CONVERSATION`, `SELF`…), weighted by importance, and decay on a per-type half-life — a fact about you lasts years, a chat line lasts a week. Repeated experiences **dedupe into one memory that gets stronger** instead of piling up. Capped at 120, pruned by relevance, recalled with weighted randomness and a six-hour "don't repeat yourself" guard.
- **Journal** is the readable record: discoveries, projects, games, mischief, milestones, things he drew for you. Capped at 400 entries and browsable in-app.
- When he comes over to talk, he may bring back an old inside joke or something you told him weeks ago — that's `recall` doing its job.

## Discovery, collection and projects

- **16 catalog items** to find while exploring: a strange screw, a tiny battery, a bent spoon, a glowing pebble… Each has a found line and a **hidden purpose** that reveals itself hours later (sometimes only after he owns a second specific item).
- **7 project templates** he can start on his own once he has parts: gather → build → *done*, *failed*, or *evolved into something else*. Success depends on his confidence and patience, the project's difficulty, and how many times he's already tried. On failure the parts come back — nothing is lost forever, and he is briefly embarrassed.
- **6 pranks** he can pull when mischief and boredom line up (sock on the desk lamp, note on the monitor, tower of screws), plus drawings left on the wall above his bed.
- Everything lands in the journal and the collection screen.

## Mini-games

Four games you can play with him, each with win/loss/streak records he keeps:

| Game | Notes |
| --- | --- |
| Rock, paper, scissors | Best of rounds; he has a personality-tied favourite throw and counters you if you keep repeating |
| Memory (cards) | His memory is deliberately imperfect — recall scales with his curiosity and energy |
| Reaction | Tap when his antenna turns green; **his reflexes depend on how awake he is** |
| Tic-tac-toe | Minimax, but a tired or nervous Pipo makes mistakes; confidence lowers the rate |

He tracks which game you play most, and may come over and ask for it.

## Android phone interactions

Pipo only acts on the phone when you **ask him** ("Pipo, turn on the flashlight"), never on his own initiative. The parser is deterministic and unit-tested:

- **Torch** on/off/status via `CameraManager.setTorchMode` (no CAMERA permission — he never opens the camera himself)
- **Media**: play/pause/next/previous, volume up/down, open YouTube/Spotify with a query
- **Camera & gallery**: camera, selfie, open gallery/photos
- **Apps & settings**: calculator, clock, browser, settings (Wi-Fi/Bluetooth/display/battery/date & time), each as an intent — some are marked "blocked" and he only takes you to the right settings page
- **Utilities**: time, date, battery level, timers, alarms (with a confirmation question), URLs, web search, maps/directions, dial a contact, copy text, share text
- **Maths**: `12*7`, `25 percent of 80`, `5 km to miles`, `100 c to f`, `2 hours in minutes`

He also *reads* safe phone state — charging, battery, music playing, headphones, online — so his mood can react to reality. Those are normal, auto-granted permissions.

## Voice and AI

- **Speech**: Android TTS with per-mood delivery (pitch and rate) — sleepy is slower and lower, excited is faster and higher. A procedurally synthesized beep voice (`SoundSynth`) is the fallback when no TTS engine answers, and there's a **silent** mode too.
- **Listening**: tap the mic and he asks for `RECORD_AUDIO` *at that moment*, not at install.
- **Optional AI**: bring your own API key and a small model can rephrase his replies. The rule-based `LocalBrain` always owns the actual state effects (name learning, phone commands, moods, memory); the AI may only colour the wording and must stay consistent with what the local brain decided. It is **off by default** and is the only reason the `INTERNET` permission exists.

## Proactive notifications

Pipo occasionally tells you about something that actually happened to him — a discovery, a finished project, a prank, a spontaneous thought, "want to play?".

Sending is deliberately constrained (`NotificationPolicy`, unit-tested):

- never while the app is in the foreground — he doesn't nag while you're looking at him
- quiet hours (default 22:00–08:00, wraps midnight)
- nothing within 90 minutes of you interacting with him
- minimum gap and per-day cap by frequency (Rare / Normal / Frequent)
- importance threshold tuned by his sociability — a shy Pipo messages less
- **if you ignore three in a row, he goes quiet** and the gap more than doubles. Silence is part of his personality.

Tapping a notification drops you straight into his room, and he shows you the thing he wanted to show you.

## Privacy and local-first philosophy

- One JSON file on the device. Written atomically (tmp + rename) under a single lock, so the UI and the worker never corrupt each other. A corrupt file is preserved as `pipo_state.corrupt.json` and the app starts fresh instead of crashing.
- **No account, no backend, no telemetry, no ads, no tracking.** Nothing about Pipo leaves the phone.
- `INTERNET` exists solely for the optional AI rephrasing; `ACCESS_NETWORK_STATE`, `SET_ALARM` are normal permissions; `RECORD_AUDIO` and `POST_NOTIFICATIONS` are requested at the moment they're needed.
- Your API key, if you add one, is stored in that same local file and only ever sent to the endpoint you configured.

## Tech stack

| | |
| --- | --- |
| Language | **Kotlin** 2.0.21 |
| UI | **Jetpack Compose** (Compose BOM 2024.10.01), Material 3, **AndroidX** |
| Rendering | Canvas-drawn robot, room and items (`ui/render`) — no image assets |
| Async | Coroutines, StateFlow, WorkManager |
| Persistence | `kotlinx-serialization` JSON, single file |
| Tests | JUnit 4 (plain JVM tests over the engines) |
| Package | `com.pipo.robot` |
| SDK | **Android SDK 35** (compile & target), **minSdk 26** |
| Toolchain | **Gradle 8.9** (wrapper), AGP 8.7.3, JDK 17 |

Source layout: `engine/` (mood, behavior, conversation, phone commands, simulation — pure Kotlin, no Android imports), `data/` (models, catalog, repository), `ui/` (Compose screens, painters, games), `notify/`, `voice/`, `phone/`, `ai/`.

## Build instructions

Requirements: JDK 17 and the Android SDK (set `sdk.dir` in `local.properties`, which is git-ignored).

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

Debug APK:

```
app/build/outputs/apk/debug/app-debug.apk
```

On macOS/Linux: `./gradlew assembleDebug` and `./gradlew test`.

## Current status

**Verified**

- Compiles cleanly and `assembleDebug` produces an installable debug APK (release builds are signed with the debug key so they install out of the box).
- **19 unit tests pass** (`EngineTest`, `PhoneCommandTest`): mood derivation and transient overrides, personality-weighted activity choice, bounded offline catch-up, projects reaching an ending, memory dedupe/prune/recall, notification cooldowns, quiet hours (including midnight wrap), ignored-notification backoff, greeting selection, chat name-learning plus a "never sounds like an assistant" guard, phone-command parsing and maths.
- `.gitignore` keeps `local.properties`, `.gradle/`, `build/`, APKs, keystores and `.env` out of the repo.
- CI workflow (`.github/workflows/build-apk.yml`) is committed; the first run failed in the runner's `setup-android` step — an environment problem, not app code — so CI is **not** currently green.

**Still needs physical-device testing** (code exists, runtime behaviour is unproven on hardware)

- Rendering, animation, camera/parallax and touch (poke, tap-to-react) on a real screen
- TTS availability/pitch, beep fallback, speech recognition accuracy
- Notification delivery, permission flow on Android 13+, WorkManager timing and the hourly catch-up on a real device
- Torch, media intents, alarms/timers, app deep links — anything that crosses into another app
- Doze/battery behaviour, process death and state restore across restarts
- End-to-end feel: pacing of dialogue, notification frequency, whether he's actually fun to be around

Treat everything above as "written and unit-tested", not "shipped and lived with".

## Project philosophy

1. **Character over utility.** If a feature makes him more of a person and less of a tool, it wins.
2. **He never sounds like an assistant.** "How can I help" is banned by a test. So is "certainly", "command", and "options".
3. **No guilt, no dark patterns.** Loneliness is capped, ignored notifications make him quieter, and absence never costs you relationship points.
4. **Failure is content.** A project that explodes is a journal entry and a story, not an error state.
5. **Silence is personality.** A robot who messages you four times a day is a notification setting, not a companion.
6. **Local-first.** Your robot lives in your phone. No cloud is required for him to exist.
7. **Bounded numbers everywhere.** Traits, memories, events, journal, cooldowns — everything is clamped so he stays a little robot and never a simulation of one.
8. **Test the soul, device-test the body.** Mood, behavior, memory and notification policy are pure functions with tests; pixels, sensors and other apps get validated on hardware.
