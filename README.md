# Pipo 🤖

> A tiny robot who lives inside your phone.

Pipo is a character-first Android app about a small, curious, slightly mischievous robot. He has his own moods, memories, habits, projects and opinions, and he lives in a little room inside your phone.

The whole product fits in one sentence:

> "I wonder what Pipo is doing."

Open the app and you find out. He might be asleep with one eye open (pretending), lying on the floor staring at the ceiling, halfway through a project that keeps catching fire, chasing a moth around the desk lamp, or peeking in from the edge of the screen because he heard you come back.

Everything runs on your device. There's no account, no server and no analytics. It's a robot and a JSON file.

---

## What is Pipo?

**Pipo is not an assistant.** He isn't a productivity app, a chatbot in a robot costume, or a dashboard. Nobody asks Pipo for a to-do list. He's the one with things to do.

He's a simulated character with persistent state that keeps evolving:

- **A body in a room.** He walks, runs, sneaks, sits, lies down, gets up, falls over, spins, dances, turns his back on you when he sulks, and stands at the window watching birds.
- **A mind with opinions.** Traits, mood, memories and unfinished projects decide what he does next, what he says and how he says it.
- **A life that goes on without you.** Nothing runs in the background, but when the app reopens (or the hourly worker fires), the time you were away is replayed through the same behavior engine. When you come back, something has changed, and he'll tell you about it.
- **A relationship, not a service.** It grows slowly with shared time and never shrinks because you were away. There's no score on screen. You notice it in how he acts.

There's no objective, no streak to protect and no score for the user. The reward is watching him.

---

## ✨ A Living Little Character

### Personality
**Ten traits** are rolled at first run, so no two Pipos are identical: curiosity, playfulness, mischief, affection, confidence, sociability, patience, laziness, adventurousness and stubbornness. They drift in tiny, bounded steps based on what he actually does. "Shy" isn't stored anywhere. It emerges from low sociability plus low confidence.

### Moods
**Twelve moods** (happy, excited, curious, sleepy, bored, grumpy, lonely, nervous, proud, embarrassed, mischievous, relaxed) are derived from continuous vectors: energy, boredom, loneliness, irritation, excitement, curiosity, happiness and affection. Short-lived **transient moods** override the derived one: proud after finishing something, embarrassed after a failure, nervous when picked up.

Mood isn't decoration. It changes which activities he considers, his idle body language, his face, the color of his glow, his voice's pitch, rate and volume, and the tone of any notification.

### Emotional body language
Each mood has its own physical signature, and transitions are blended rather than snapped:

| Mood | What his body does |
| --- | --- |
| Happy | Bouncy sway, antenna wiggles, hums (a "hum-sway" fidget) |
| Excited | Hopping, arms up, fast antenna |
| Sleepy | Slow, drooping head, heavy eyelids, yawns (with a yawn sound) |
| Curious | Leans forward, head tilt, dilated pupils |
| Embarrassed | Turns away, hands over his face, blush |
| Grumpy | Arms crossed, half-turned away, impatient foot tap |
| Proud | Upright, chest out, smug squint |
| Nervous | Small trembling, darting eyes, shrunken pupils |
| Mischievous | Crouched, side glances at you, suspicious squint, sneaking |
| Lonely / sad | Deflated posture, drooping antenna, a single glinting tear |

### Autonomy
He decides for himself what to do next. Nothing runs on a script loop. See [Autonomous life](#autonomous-life).

### Curiosity and mischief
He discovers things, investigates them, builds with them, and occasionally does small crimes against the room. See [Discovery & Projects](#-discovery--projects) and [Mischief](#mischief).

---

## 🌎 Pipo's World

### The room
A side-scrolling room about 2.4 screens wide that you can pan by dragging. It contains his **bed** (with blanket), a **plant**, a **charging station** that shows your phone's real battery, a **window** onto the outside, a **desk** with a computer and lamp, a **shelf** for his discoveries, a **pegboard and workbench**, an **arcade cabinet**, a **toy box** and ball, a **real-time clock**, and the **drawings** he makes of you, which hang above his bed.

### Interactive objects
Tap an object and he might go and use it (or refuse, depending on how stubborn or grumpy he is). He remembers what you encourage. If you keep suggesting the plant, visiting the plant becomes a habit.

### Day and night
Lighting follows the real clock. Morning light gives way to an orange evening sky and then a starry night with a moon. At night the desk lamp throws a warm cone and a pool of light, the monitor and arcade glow, and the whole room dims.

### Environmental life
- A **sunbeam** comes through the window with drifting **dust motes**. Its angle follows the actual hour.
- A **moth** circles the desk lamp at night. A **bird** crosses the window during the day.
- The **plant rustles** when Pipo brushes past it and sways to music.
- The **charging station** pulses and sends up energy motes while your phone charges.
- **Welding sparks** fly at the workbench while he builds, and the **monitor** shows scrolling text, or a music visualizer when music plays.
- The curtains breathe, the clock tells the real time, and faint dust drifts through the air.

These aren't only backdrop. His attention system looks at the same moth, bird and ball the renderer draws.

---

## 🎨 3D / Visual Experience

### What it actually is: **2.5D depth rendering**, not a 3D engine

Pipo is **not** rendered with OpenGL, Filament, a mesh or a 3D model. Everything is drawn procedurally in code on a Jetpack Compose `Canvas`, with no sprite sheets and no bitmaps. What makes it feel physical is a custom **2.5D projection and lighting layer**:

**Character rendering (`ui/render/PipoPainter.kt`)**
- Every body part (head, face screen, ear pods, torso, arms, legs, feet, antenna) has a position in a small **local 3D space**: x across his body, z toward the viewer.
- Parts are **projected through his current yaw** (rotation around his vertical axis) and **depth-sorted**, so the far ear, arm and leg are drawn behind his body and shaded darker.
- He can turn through a **front → three-quarter → profile → back** view. The face screen foreshortens and slides around the head, a side panel of the head appears, and from behind you see his back vents and battery hatch. When he sulks, he really does turn his back on you.
- The **head has its own yaw**, so his head leads his body when he looks at something.
- Spinning is a real rotation through these views, not a horizontal squash.

**Lighting and shading**
- Each shell part gets a **key-light/shade gradient**, a top sheen, a **rim light** on the opposite edge, and a **specular glint** on the lit side.
- The light isn't fixed. `pipoLight()` works out which of the room's **real light sources** is lighting him: the window by day (warm orange in the evening), the desk lamp at night, the charger's cyan glow while charging, the monitor and the arcade. His highlights switch sides as he walks past the lamp, and the second-strongest source becomes his rim light. Turn on the flashlight and he's lit from his own antenna.
- **Contact shadow:** soft, dark and tight at his feet, wider and fainter when he jumps or is carried, and stretched away from the light (long shadows under the lamp at night). There's also ambient occlusion under his feet and a shadow from his head onto his body.
- His **face screen** has a glass reflection band, a bezel highlight and eye-glow bloom. The pupils are brighter cores inside each eye that travel further than the eye outline, which reads as looking.

**Environment depth (`ui/render/RoomPainter.kt`)**
- **Perspective floor:** board seams converge on a vanishing point at the center of the screen.
- **Parallax layers:** the view out of the window (hills, sun, moon, clouds, stars, birds) moves slower than the room, and a **foreground layer** of out-of-focus objects on the floor (a screw, a coiled cable, a toy block) moves faster than the room.
- **Phone-tilt parallax:** the accelerometer (already used for shake detection) shifts the far and foreground layers, so tilting the phone gives a little depth.
- **Contact shadows under all furniture**, ambient occlusion where the wall meets the floor, gradient-shaded wood, a lit plant pot and leaves, and a shaded arcade cabinet with scanlines.
- **Light volumes:** the sun or moon shaft through the window, the lamp cone, and additive glows blended with `BlendMode.Screen`.

**Camera**
- It follows Pipo smoothly across the room, and you can drag to pan (it holds your framing for a few seconds).
- It **dollies in** a little (up to about 1.12×) when he talks to you, presents a discovery or listens to you. It also creeps in slightly when he's asleep at night. Touch input is converted back through the zoom, so poking still hits the right spot.

### Animation system (`ui/render/Rig.kt`)

| Layer | What's implemented |
| --- | --- |
| **Idle** | Breathing, blinking (single and double, slower when tired), autonomous saccades plus tiny micro-saccades, and **fidgets** layered on calm poses: looking around, glancing at you, shifting weight, antenna twitch, looking at his hand, scratching his head, foot tapping, hum-swaying, sighing, yawning, small hops. Which fidgets he picks depends on mood and energy. |
| **Locomotion** | Walk, run, **sneak** (tiptoe burglar walk), hop, spin, sit, **lie down**, **get up**, fall, recover, dance, being carried. A **spring-driven yaw** turns him toward where he's walking and overshoots slightly on turns and stops. Running stops end in a skid squash. |
| **Secondary motion** | The antenna is a **damped spring** pushed by his body's acceleration, so it lags, wobbles after stops and whips on landings. **Landing impacts** squash and stretch him (hops, drops, skids). |
| **Facial** | 21 expressions interpolated parameter by parameter: eye open, scale, eyelid height and angle, happy arcs, closed, round (surprised), asymmetry, blush, sparkle, squint, **wink**, **dizzy spirals**, **tear**, **pupil dilation**, and mouth curve, open, width, skew and wave. |
| **Emotional** | Per-mood idle poses (see the table above), plus poses for arms crossed, turn away, sulk, laugh, sigh, yawn, dizzy, celebrate and "finger up (one sec)". |
| **Speech** | The mouth is driven by **the actual letters being said**: open on vowels, closed on m, b and p, pauses on punctuation, and bigger on ALL-CAPS words, which also brighten his eyes and bob his head. The speed follows the voice's speech rate for his current mood. Line style changes his body: a question tilts his head at the end, a whisper makes him lean in, excitement makes him bounce, a laugh brings a laughing face and bounce, and a sigh deflates him. |
| **Gaze / attention** | When nothing directs his eyes, an attention system picks what to look at: **you** (more often the closer you two are), the moving ball, the moth, a bird at the window, whatever he's working on (almost nothing else while he's absorbed), or the charger. Big gaze shifts come with a blink. He looks at where you touch, looks where he's going, and **notices you when you open the app**. |
| **Environmental** | The plant reacts to him, the ball rolls, and the lamp, monitor, arcade, charger and workbench animate based on what he's doing. |

---

## Autonomous life

`BehaviorEngine` scores 18 activities as a function of **traits × mood × environment × his own recent life**, then picks with controlled randomness among the top few:

> sleep · rest · charge · explore · play with the ball · arcade · experiment · build · examine his collection · rearrange (mischief) · read · think at the window · computer · check on the plant · dance · prepare a surprise · come find you · **do absolutely nothing**

What feeds the choice:
- **Personality and mood**, as before.
- **Time of day:** plant and window in the morning, reading and resting in the evening, sleep at night.
- **Environment:** charging pulls him to the charger, music makes him dance.
- **Variety:** activities he's done recently lose appeal, so he doesn't loop.
- **Unfinished projects:** missing parts send him exploring, and a recent failure nags at him until he tries again.
- **Memory:** things you've encouraged become habits, and games you play together make him practise at the arcade.
- **You:** if you just interacted he feels less need to seek you out and more urge to show off. The relationship makes him want to be near you.

And the parts that make him feel alive rather than scheduled:
- **Doing nothing is a real activity.** It comes in flavors: sitting, standing around, or lying flat on the floor staring at the ceiling and then getting back up. The closer you two are, the more often he does nothing near the front of the room, near you.
- **Getting absorbed.** Focused activities can pull him in. They take more than twice as long, his eyes lock onto the work, and if you poke him he holds up one finger ("One sec. I'm in the zone.") instead of stopping. Poke again and he gives in.
- **Getting distracted.** An impatient, curious or bored Pipo gets pulled away mid-task: the ball moves, a moth flies by, a noise, a shiny thing, a thought. Sometimes he goes back to what he was doing ("Anyway."), sometimes he forgets it completely, and sometimes chasing the shiny thing turns into an **unexpected discovery**.

---

## Mischief

Mischief comes from his mischief trait, boredom and mood, not from a timer:
- **Eight room pranks:** a hat on the plant, a sock on the lamp (which turns its light purple), a note on the monitor, the ball tucked into his bed, an "arcade high score", a tower of screws, **the ball hidden behind the plant pot**, and **the clock turned sideways**. He does them by **sneaking** there, with a suspicious glance back at you. He cleans them up again eventually. Tap a prank and he'll deny everything.
- **Pretending to sleep:** you open the app and he's in bed "asleep", but one eye keeps opening to check on you. Poke him and it's *"BOO! I was awake the whole time."* If you don't notice, he gives up and complains that you didn't notice.
- **Appearing unexpectedly:** he tiptoes in from the edge of the screen, peeks at you and then walks over as if nothing happened.
- **Running away** when poked in a mischievous mood, and "boop, got you back".

Pranks are rare and bounded, and they never block the app or ask anything of you.

---

## 🧠 Memory

`Chronicle` is his memory system:
- **Persistent memories** are typed (user fact, joke, discovery, game, project, moment, conversation, event, self) and weighted by importance. Each type decays on its own half-life: a fact about you lasts years, a chat line about a week. **Repeated experiences strengthen one memory** instead of piling up. There are at most 120, pruned by relevance and recalled with weighted randomness, with a six-hour guard against repeating himself.
- **Memories change behavior:** encouraged activities become habits, games you've played shape what he asks to play and what he trash-talks about, a failed project gets retried, and he brings up inside jokes and things you told him when he comes over to talk.
- **The journal** is the readable record: discoveries, projects (including retries: "Attempt 3. He never gave up, and he'd like that noted."), games, mischief (including the fake nap), milestones and the drawings he made you. It holds at most 400 entries.
- **Coming back:** while you're away he keeps a short **away log** of what actually happened. When you return (after 45+ minutes), he gives you a recap in his own voice: *"Okay, recap: I found a glowing pebble, broke the lamp thing, and gave the plant a hat. I regret nothing."* If nothing happened, he says so ("did absolutely nothing. On purpose"). It's a report, never a guilt trip, and a unit test checks that.
- **Relationship through behavior:** there's no number on screen. A closer relationship shows up as him running to greet you after a long absence, hanging out near you, glancing at you more often and seeking you out.

---

## 🔎 Discovery & Projects

**live → explore → discover → collect → build → remember → continue living**

- **Discover:** 16 catalog items (a strange screw, a tiny battery, a bent spoon, a glowing pebble…), found by exploring or by chasing a distraction. Discovery prefers parts his current project needs.
- **Investigate:** each item has a found line and a **hidden purpose** that reveals itself hours later, sometimes only once he owns a specific second item.
- **Collect:** finds go on the shelf in his room and in the collection screen.
- **Build:** 7 project templates. A project goes from gathering to building to **done**, **failed** or **evolved into something else**. Building isn't a straight line: there are **stages** ("The frame is done. It stands up on its own. Mostly."), **setbacks** ("A small fire. Very small. He blew it out.") and **breakthroughs**. The reveal card tells the story of the build.
- **Fail and try again:** on failure the parts come back, he's embarrassed for a bit, and within a few days a stubborn or patient Pipo **retries**, carrying his experience over (each attempt raises the success chance).
- **Remember:** every stage lands in the journal and in his memories, and he mentions it when he comes back to you.

---

## 🎮 Games

Four games, each tracking wins, losses and streaks:

| Game | How Pipo plays |
| --- | --- |
| Rock Paper Scissors | First to 3. He has a favorite throw tied to his personality, and counters you if you keep repeating. |
| Memory Match | His memory is deliberately imperfect. Recall scales with his curiosity and energy. |
| Reaction Race | Tap when his antenna turns green. His reflexes depend on how awake he is. |
| Tic-Tac-Toe | Minimax, but a tired or nervous Pipo makes mistakes. |

Playing should feel like playing with him:
- He **opens with your history**: *"You're ahead 7–4 overall. Not for long."*, *"You beat me 3 times in a row last time. I've been training."*
- **Round streaks:** two wins in a row get a laugh and a tease, three get a celebration. Two losses make him turn away, three make him **sulk with his back to you**.
- He **gets distracted** if you take too long: he yawns if he's tired, crosses his arms if he's impatient, or looks around for a moth.
- Back in his room he reacts to the result with his whole body. After a **3-game streak** either way he brings it up and offers a **rematch**.
- Results become memories and journal entries, and your most-played game is the one he asks for.

---

## 📱 Android Integration

Pipo only acts on the phone **when you ask him** in chat or by voice. Every action is staged as him doing it:

- **Flashlight** on, off or status via `CameraManager.setTorchMode` (no CAMERA permission). *"Emergency sunshine."* His antenna becomes the light source for the whole scene.
- **Music:** play, pause, next, previous, volume up and down, open YouTube or a music app with a query. When music plays, **he dances**, the plant sways and the monitor shows a visualizer.
- **Charging:** plug your phone in and he **heads straight to his charging station**. The station shows your real battery level.
- **Camera and selfie:** a confident Pipo poses, a shy one hides.
- **Photos:** say something like "look at this photo" or "let me show you a picture" and the **system photo picker** opens (no storage permission). He reacts to the photo you explicitly chose, based on local color and brightness analysis. The photo is never stored or uploaded.
- **Apps and settings:** calculator, clock, browser, settings pages (Wi-Fi, Bluetooth, display, battery, date and time).
- **Utilities:** time, date, battery level, timers, alarms (with a confirmation question), URLs, web search, maps and directions, dialing, copy and share.
- **Maths:** `12*7`, `25 percent of 80`, `5 km to miles`, `100 c to f`, `2 hours in minutes`.
- **Shake the phone** and he falls over, gets annoyed or enjoys it, depending on his personality.

He also reads safe phone state (charging, battery, music playing, headphones, Bluetooth audio, online or offline) and reacts to it.

---

## 🎙️ Voice & AI

The architecture keeps these separate:

| Layer | Owns |
| --- | --- |
| **Pipo engine** (`engine/`, pure Kotlin) | Personality, mood, memory, autonomy, projects, notifications policy. The soul. |
| **AI** (`ai/ChatBrain.kt`, optional) | Only the *wording* of chat replies. |
| **Android layer** (`phone/`, `voice/`, `notify/`) | Phone actions, speech in and out, notifications. |

- **Voice:** Android's built-in TTS (usually offline). Mood sets pitch and rate, and **the line itself adjusts delivery**: whispers (lines in parentheses, or starting with "psst") are quieter and slower, all-caps or "!!" lines are higher and faster, and sighs are lower. A **giggle chirp** plays before laugh lines and a **sigh** before sigh lines. Robot sounds (beeps, boops, giggles, "hmm"s, yawns, a servo whirr when he breaks into a run) are **synthesized on the fly**, so there are no audio files. There's a beeps-only chirp voice and a silent mode.
- **Speech-synced animation:** his mouth, eyes and body follow each line as described in the animation table.
- **Listening:** tap the mic and `RECORD_AUDIO` is requested *at that moment*. Recognition runs only while the listening pill is visible.
- **Optional AI:** bring your own Anthropic API key (Settings) and replies are rephrased by Claude Haiku 4.5. The rule-based `LocalBrain` still decides every state effect (name learning, phone commands, moods, memory). The AI only colors the words and is prompted never to act like an assistant. It's **off by default** and is the only reason the `INTERNET` permission exists.

---

## 🔔 Proactive Behavior

In the app, Pipo starts things himself: he comes over wanting to play, brings up a memory, shows you what he found, offers a rematch, asks your name at a natural pause, or peeks in from the edge of the screen.

Outside the app he may send **one** notification about something that **actually happened** to him:

> "Joel. I found something weird." · "Don't judge me." · "I finally finished it." · "I have an idea." · "..."

`NotificationPolicy` (unit-tested) keeps this rare:
- never while the app is open
- **quiet hours** (default 22:00–08:00, wrapping midnight)
- nothing within 90 minutes of you interacting with him
- a minimum gap and a daily cap by frequency (Rare / Normal / Frequent), plus per-category toggles
- an importance threshold tuned by his personality (a shy Pipo messages less)
- **if you ignore three in a row, he goes quiet**, and the gap stretches 2.5×
- "Not now" is respected and remembered, without guilt

Tapping a notification opens his room, where he shows you what he meant.

---

## 🔐 Privacy

- **Local-first:** one JSON file on the device, written atomically (tmp file + rename) under a single lock. A corrupt file is kept aside and Pipo starts fresh instead of crashing.
- **No account, backend, telemetry, ads or tracking.**
- **Permissions:**
  - `RECORD_AUDIO`: requested only when you tap the mic.
  - `POST_NOTIFICATIONS` (Android 13+): requested only after Pipo asks you in-app whether he may message you.
  - `INTERNET`: only for the optional AI rephrasing.
  - `ACCESS_NETWORK_STATE`, `SET_ALARM`: normal, auto-granted.
  - The torch needs **no** CAMERA permission. Photos go through the system picker and need **no** storage permission.
- The accelerometer (shake and tilt parallax) is read only while the app is in the foreground and is never stored.
- Your API key, if you add one, stays in the local file and is sent only to `api.anthropic.com`.

---

## 🛠️ Technology

| | |
| --- | --- |
| Language | **Kotlin 2.0.21** |
| UI | **Jetpack Compose** (BOM 2024.10.01), Material 3, AndroidX |
| Rendering | Compose `Canvas`, procedural 2.5D (projected parts, depth sorting, dynamic lighting, parallax), with no image assets and no 3D engine |
| Animation | Custom rig: pose/face libraries, spring dynamics (yaw, antenna), impacts, fidgets, text-driven lip movement |
| Async | Kotlin coroutines, WorkManager 2.9.1 |
| Persistence | kotlinx-serialization JSON 1.7.3, single file |
| Audio | Android TTS + an `AudioTrack` synthesizer |
| Tests | JUnit 4, plain JVM tests |
| SDK | compile/target **35**, min **26** |
| Toolchain | Gradle **8.9** (wrapper), AGP **8.7.3**, JDK 17 |
| Package | `com.pipo.robot` |

Source layout: `engine/` (behavior, mood, memory, world, simulation, conversation, notification policy), `data/` (models, catalog, repository), `ui/render/` (rig, painter, room, scene), `ui/home/`, `ui/games/`, `ui/screens/`, `voice/`, `phone/`, `notify/`, `ai/`.

---

## 🚀 Build

Requirements: JDK 17 and the Android SDK (`sdk.dir` in `local.properties`, which is git-ignored).

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

On macOS or Linux: `./gradlew test` and `./gradlew assembleDebug`.

---

## 📊 Current Status

This section is deliberately literal.

### ✅ Implemented (in code)
Everything described above: the 2.5D renderer and lighting, parallax and camera, the animation rig (springs, fidgets, speech-driven mouth, new poses and expressions), attention and gaze, absorption and distraction, rest variants, memory-driven habits, project stages and retries, the away recap, the new mischief (sneaking, fake sleep, peek-in, two new pranks), game personality (history, streaks, distraction, rematch), style-aware voice delivery and new synthesized sounds, notification copy, and all the pre-existing features.

### 🧪 Automated-tested
**40 JVM unit tests pass** (`EngineTest` 15, `EvolutionTest` 21, `PhoneCommandTest` 4). They cover:
- mood derivation, personality-weighted choice, bounded offline simulation, projects reaching an ending, memory dedupe and prune, notification cooldowns, quiet hours and backoff, greetings, chat name-learning with a "never sounds like an assistant" guard, and phone-command parsing and maths
- **evolution pass:** variety penalty, encouraged activities becoming habits, absorption (only for focused work, and longer), no distraction while absorbed or asleep, the distraction cooldown, failed-project retries (and letting old failures go), bounded project logs, the away recap format and its no-guilt wording, the offline away log, the new mischievous greetings, every prank having a place in the room, "I have an idea" notifications, speech-style detection
- **animation rig:** he turns his back when sulking and faces you again afterwards, faces the direction he walks, the mouth opens on vowels and closes on "m", landing squash recovers, and every pose × expression stays finite
- **lighting:** at night the key light comes from the lamp's side and is stronger near it

These tests check the logic and the animation math. **They don't check what anything looks like.**

### 🏗️ Successfully built
`.\gradlew.bat test` and `.\gradlew.bat assembleDebug` succeed on Windows with this repo, producing `app/build/outputs/apk/debug/app-debug.apk` (about 10.8 MB).

### 📱 Physically tested
**Nothing in this version has been run on an Android device or emulator yet.** No device was connected and no emulator is installed on the build machine. That means all of the following is unverified on hardware:
- how the 2.5D rendering, lighting, parallax, tilt and camera zoom actually **look**, and whether the proportions, colors and turn views read well
- frame rate, battery and memory use of the richer per-frame drawing (it hasn't been profiled)
- how natural the fidgets, the speech-to-mouth timing (which is estimated from TTS speech rate, not synchronized to real audio), and the gaze and attention timing feel
- touch accuracy while the camera is zoomed
- TTS whisper volume, synthesized sounds, speech recognition
- notification delivery, WorkManager timing, permission flows on Android 13+
- torch, media, camera and settings intents, and anything else that crosses into another app
- process death and state restore, including loading state saved by the previous version (new fields have defaults, so it's expected to load)

CI (`.github/workflows/build-apk.yml`) is committed. Its status hasn't been rechecked for this change.

---

## Project philosophy

1. **Character over utility.** If a feature makes him more of a person and less of a tool, it wins.
2. **He never sounds like an assistant.** A test bans "How can I help".
3. **No guilt, no dark patterns.** Loneliness is capped, ignored notifications make him quieter, absence never costs relationship, and the "while you were gone" recap is a story, not a complaint.
4. **Failure is content.** A project that catches fire is a journal entry, and eventually a comeback.
5. **Doing nothing is valid.** Sometimes the most Pipo thing is lying on the floor.
6. **Silence is personality.** A robot who messages you four times a day is a notification setting, not a companion.
7. **Local-first.** Your robot lives in your phone.
8. **Honest about the body.** The soul is unit-tested, and the pixels, sensors and other apps need a real phone. This README says which is which.
