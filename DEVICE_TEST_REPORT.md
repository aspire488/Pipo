# Pipo — Physical Device Test Report

| | |
| --- | --- |
| **Device** | Samsung Galaxy S23 (SM-S911B, `dm1q`) |
| **Android** | 15 (API 35), One UI |
| **Screen** | 1080 × 2340 px, 480 dpi (19.5:9), display running in its **60 Hz** mode (refresh capped at 60 by device settings) |
| **Connection** | ADB over Wireless Debugging |
| **Build tested** | `com.pipo.robot` 1.0 (versionCode 1), debug build of this commit (release build used for performance numbers) |
| **Date** | 2026-09-29 |
| **TTS on device** | Google Speech Services, offline voice `en-us-x-iom-local` (Samsung TTS is the default engine; Pipo selects the Google voice) |

## Automated results

| Check | Result |
| --- | --- |
| `testDebugUnitTest` | **61 / 61 pass** (8 test classes) |
| On-device instrumentation (`PoseGalleryTest`) | **6 / 6 pass** on the S23; rendered sheets reviewed by eye |
| `lintDebug` | **0 errors, 12 warnings**: 9 × newer dependency available (not upgraded during a polish phase), 2 × intentional portrait lock, 1 × `mipmap-anydpi-v26` (required by `aapt2` for adaptive icons; merging it breaks the build, verified) |
| `assembleDebug` / `assembleRelease` | Success (≈11.1 MB / ≈7.4 MB) |
| GitHub CI | **Not checked** in this pass |

---

## VERIFIED ON PHYSICAL DEVICE

Evidence came from screenshots, the `PipoDebug` / `PipoVoice` / `PipoBrain` logcat tags (positions, events and timings, never message content), `dumpsys gfxinfo`, `/proc` CPU ticks, `simpleperf`, and the persisted state file read with `run-as`.

**Launch and first impression**
- Installs and launches with no crash. First run: asleep in bed → tap → "Hi. …I'm Pipo. I live here. This is my room."
- Left alone, he chooses activities by himself (seen: explore, read, think, charge, play toy, arcade, dance, rest/lying on the floor, console), with distractions and "one sec" absorption ("Hold on. This is the good part.").

**Procedural 2.5D renderer**
- Turntable on the phone's own Canvas: front, 3/4 (±0.6, 1.0), side (±90°), back-3/4 and back all read correctly. The head is shallower than it is wide, the face screen foreshortens, and the side panel and back vents are visible. Head leads body.
- 24 poses reviewed: sulk and turn-away (back turned), arms crossed, embarrassed, lie down, sitting, sneak, dancing, celebrate, yawn, dizzy, laugh, fallen, hiding, thinking, finger-up ("one sec"), reading, presenting, wave, peek, phone, gaming, stretch. Plus the get-up sequence and every expression.
- In the live room: walking in three-quarter (face visible), lying in bed, sitting and gaming turned to the TV, and being carried (arms up).

**Lighting and environment (live, evening/night) and offscreen renders (day, evening, night)**
- Night lamp cone and glow, the charger glow, monitor text, the moon, and string lights that are warm at night.
- The TV and console glow while he plays, and the TV screen shows its game clipped inside the bezel.
- Offscreen renders at 09:00, 13:00, 15:00, 18:48, 21:30 and 23:30 show day/evening/night lighting and the key light following the lamp position.

**Camera and touch**
- Tap on Pipo at zoom 1.00 → hit. Tap on the top of his head while the camera is **zoomed to 1.065** → hit (surprised reaction). Visual and hit-test positions agree.
- Drag to carry: he follows the finger with arms up and a nervous line. **(Bug found and fixed, see below.)**
- Tap on the floor while he's happy → he runs to it (tap chase).
- Drag the floor to pan, then tap furniture (the TV) → hit at the computed world position.

**Voice**
- Toddler voice: every line is rendered and shifted (24 kHz → 34.8 kHz). **Render latency 69–778 ms**; typically under 200 ms, and the first line after launch takes ≈500–780 ms.
- Cold start: the first spoken line now waits for TTS to be ready and is audible. **(Bug found and fixed.)**
- In-game speech with a synced mouth (20+ lines played during a match).
- The owner listened and approved the voice after two iterations ("not girl" → "not grown man" → toddler).

**Conversation**
- **Gemini** replies in character in **1.1–1.7 s**. It follows the conversation (the follow-up "how does it fly" gets an answer about the flying machine from the previous turn) and keeps actions from the local brain ("can you dance" → he dances).
- **Groq fallback** (Gemini disabled with a debug flag): replies in 1.4 s.
- **Both down** → instant offline reply.
- **Voice conversation** (mic → recognition → reply): tested by the owner on the phone and confirmed working.

**Games**
- **RPS:** full match to 3 and the record persisted (`pipoWins 3, userWins 1, streak 3`). Re-opening says *"I've won 3 in a row. Want to make it 4?"*. Idling brings the boredom line *"Hm? Oh. Your turn. Still."*. Back exits.
- **Memory Match:** flip, miss, Pipo's turn ("Oops."), turn returns to you.
- **Reaction Race:** round flow, and Pipo won in 310 ms. **(Layout bug fixed.)**
- **Tic-Tac-Toe:** move and reply.

**Memory, journal and returning**
- The journal lists real events (found items, games, the drawing he made).
- Pipo's Things shows 3 found items and the active project "Flying machine, collecting parts".
- **Away recap** (last-seen time moved back 7 h, 3 h and 5 h in the saved state): *"While you were gone I had an idea. A flying machine…"*, *"…I did absolutely nothing. On purpose."*. Every recap reflects simulated events, and none is guilt-tripping.

**Android lifecycle**
- Home → return: a brief greeting, the activity resumes, nothing duplicated.
- **Process death** (`am kill` while backgrounded) → relaunch: new PID, no crash, state restored.
- Screen off → lock screen → owner unlock: the app returned to the foreground with no crash.

**Screen time (phone and console)**
- Suggesting the TV → he walks to the rug, sits turned to the TV, and plays.
- The session ends by itself after about 13 s.
- Suggesting again → *"I already played! Screen break."* (the budget is enforced).
- Tapping him mid-console → he stops and greets you immediately. **(Two bugs found and fixed, see below.)**

**Settings, menus and privacy**
- Settings: name, notification controls, the new "Chatting" disclosure and the notification-noticing section. The owner confirmed scrolling and the layout.
- Permissions requested at runtime: only `RECORD_AUDIO` (on mic tap) and `POST_NOTIFICATIONS` (when asked). No camera, contacts, SMS, location or storage permissions exist in the manifest. Notification access stays **off** unless the user grants it.

---

## Performance (measured, not estimated)

The display runs at 60 Hz, so ≈16.7 ms is the frame budget. Pipo's behaviour is autonomous, so each window mixes idle, walking and activities. The scenarios could not be perfectly isolated.

| Build / window | Frames | Janky | Frame time p50 / p90 / p99 | GPU p50 / p90 |
| --- | --- | --- | --- | --- |
| Debug, 20 s | 1204 (≈60 fps) | 4 (0.33%) | 18 / 20 / 24 ms | 5 / 6 ms |
| Release, before colour fix, ≈20 s | 1127 | 1 (0.09%) | 13 / 20 / 30 ms | 5 ms |
| Release, **after** colour fix, ≈20 s | 1133 | 2 (0.18%) | **11 / 16 / 28 ms** | 4 ms |

(`dumpsys gfxinfo` percentiles include the whole pipeline, so p50 above 16 ms in debug doesn't mean dropped frames. The janky counts are the real signal.)

- **Memory (debug):** PSS ≈153 MB (Graphics ≈61 MB, Java heap ≈18 MB, native ≈16 MB).
- **CPU (release, `/proc/<pid>/stat` over 20 s):** ≈89% of one core, about 11% of the 8-core SoC. Most of it is on **RenderThread** (≈1.4× the main thread's ticks), which means Canvas command generation (gradients, paths, blend modes), not Kotlin logic.
- **Profile (`simpleperf`, debug):** besides debug-build interpreter overhead, the top app-level hotspot was Compose's `lerp(Color, Color)` → `Color.convert` / `ColorSpace.connect` (Oklab conversion on every call, dozens of times per frame). Replacing it with a packed-sRGB blend improved release frame time from p50 13 → 11 ms and p90 20 → 16 ms.
- **Layer attribution** (debug `skip=` flags) was too noisy to rank layers, because behaviour varies between runs. No single layer dominated.
- **Not measured:** battery drain over time, a thermal soak, a 120 Hz display mode, and allocation rate or recomposition counts.

---

## Bugs found on the device and fixed

Each one was reproduced on the S23, fixed, rebuilt, reinstalled and re-checked on the phone.

| # | Bug (seen on device) | Cause | Fix |
| --- | --- | --- | --- |
| 1 | Room filled only the top half of a 19.5:9 screen, leaving a big band of empty floor | World scale was width-limited at 100 u | Scale `min(w/88, h/185)`, floor line at 0.64 h; Pipo ≈14% larger |
| 2 | Foreground screw was hidden behind the mic button | Foreground baseline was fixed at 0.885 h | `SceneGeo.foregroundY`, halfway down the floor band |
| 3 | Asleep in bed, his antenna and head poked out past the headboard | Bed pivot too far left | `BED_PIVOT` 30 → 34, blanket trimmed to the bed end |
| 4 | Panning the room while zoomed moved it faster than the finger | Pan delta not divided by zoom | `dx / u / camZoom` |
| 5 | A carried Pipo drifted ≈120 px away from the finger | The camera kept re-centring on him during the drag | Camera holds during a carry; edge-scroll takes him along |
| 6 | Voice sounded like a girl, then like a grown man | Female TTS pitched up; then a male voice with pitch only | On-device toddler voice: male voice + 1.45× playback (pitch **and** formants) |
| 7 | First line after a cold start was silent while his mouth moved | TTS engine not yet initialised (took over 3 s) | Say beats wait for `voice.ready` (up to 8 s) |
| 8 | Game lines appeared only as bubbles, with no voice | `GamePipo` had no voice | `line` setter speaks with a synced mouth |
| 9 | Reaction Race headline hugged the card edge, uncentred | No padding or alignment | Centred, padded |
| 10 | High per-frame CPU from colour blending | Compose Oklab `lerp` in the painters | `ColorMath.lerp` (packed sRGB) |
| 11 | Console game drew outside the TV | Missing clip | `clipRect` to the screen |
| 12 | Pipo sat in front of the TV and hid it | Seat position | Seat moved to beside the TV |
| 13 | Gaming Pipo sometimes faced away from the TV | The pose yaw was multiplied by a random `awaySide` | Gaming always faces the TV |
| 14 | Tapping him mid-console said "Phone away." | `activity` was cleared before the line was picked | Gadget captured first; console-specific lines |
| 15 | Pulled off the console, he went straight to the arcade | The arcade ignored the screen break | The arcade waits out the break (regression test added) |

The uncommitted fixes from the previous session (head depth for side and back views, three-quarter walking yaw, hand-depth ordering, the blanket shape, the ceiling and string lights, and ignoring his own voice as "music") were built, run and checked on this phone as part of this pass.

## Added during this phase, at the owner's request
- **Toddler boy voice** (on-device pitch plus formant shift).
- **Chat brain:** Gemini → Groq → offline, with conversation history, and the Anthropic option removed. Keys are kept out of git.
- **Notification noticing** (opt-in, app + kind only).
- **His own phone and game console**, with a screen-time budget so he doesn't become addicted.

---

## NOT YET VERIFIED ON PHYSICAL DEVICE

- **Notification noticing:** Notification access was not granted during testing. The classifier and privacy rules are unit-tested only.
- **Outgoing notifications:** WorkManager timing, delivery, quiet hours and ignore-backoff on the real device. The policy is unit-tested only.
- **Android intents:** torch, media keys and volume, camera and selfie, the photo picker, settings pages, browser, YouTube and music, maps, calculator, timers, alarms, clipboard and share.
- **Truly offline behaviour:** ADB runs over Wi-Fi, so offline was simulated with the `nobrain` debug flag rather than by cutting the network.
- **Tilt parallax** by physically tilting the phone. **Shake reactions.**
- **Lock → unlock** as a timed scenario (only observed incidentally), and a long unattended session (hours).
- **Mischief:** fake sleep with one eye open, peeking in from the edge and pranks were not specifically triggered and observed in this pass (they're unit-tested).
- **Project completion, failure and retry** end to end on the device (a project was seen in progress; the lifecycle is unit-tested).
- **Daytime lighting in the live app** (testing ran 19:00–20:30). Day was checked in offscreen renders on the device's Canvas.
- **Battery impact** over time; 120 Hz mode.

## Remaining limitations
- CPU use (≈0.9 of a core in release) is acceptable for a foreground character app, but not small. The next step would be caching the static parts of the room into a layer, a structural change left out of this polish phase.
- Mouth timing is estimated from speech rate, not synchronised with the audio.
- Chat needs your own Gemini or Groq keys at build time. Builds without keys (for example CI) use the offline brain.

## Incident note
While injecting a drag gesture, an incoming Instagram heads-up notification appeared and its quick-reply field was opened. It was closed with Back and a shade collapse. **Nothing was typed or sent.** After that, the focused window was checked before each injected gesture.
