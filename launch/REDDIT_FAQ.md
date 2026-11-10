# Pipo — Reddit FAQ / reply pack

Prepared answers for the questions that always arrive in the first 48 hours. Every fact here was
checked against the shipped `dist\Pipo.apk` (v1.0) and the repo, not against unreleased work.

**How to use:** copy the *Answer* text, keep it short, and cut anything that does not apply to the
question actually asked. Do not paste the same paragraph twice in one thread.

---

## Quick facts you can quote

| Fact | Value |
|---|---|
| Package | `com.pipo.robot` |
| Version in the download | 1.0 (versionCode 1) |
| APK size | 26.08 MB |
| Android | 8.0 (API 26) and newer, target 35 |
| ABIs | arm64-v8a, armeabi-v7a (phones; no x86 emulator build in the release) |
| Signer | `CN=Pipo, O=Pipo`, RSA-4096, APK Signature Scheme v2, `debuggable=false` |
| APK SHA-256 | `911b61a523da2b0c91c747fdf87fcbfcd318b6bde458385b3bd69c2c121e3526` |
| Licence | MIT (© 2026 Joel Jigo) |
| Permissions | 13 declared; the sensitive ones are opt-in and off by default |
| Accounts / analytics / ads | none |
| Tests | 266 unit tests in 39 classes |

---

## "What is it?"

> It's a small robot who lives in a room inside your phone and runs his own day. He has traits and
> moods that change what he does, memories that fade at different rates, projects that fail and get
> retried, a pet called Nib, and a map of places he actually visits. There's no task list and no
> score — you open it to find out what he's up to.

**Do not say:** "AI companion", "your AI friend", "ChatGPT in a robot".

---

## "So it's an AI chatbot?"

> No. The character is a deterministic simulation in Kotlin: traits, moods, memories and a scoring
> engine pick what he does. An LLM, if you connect one, only chooses the *wording* of a chat reply —
> it never decides his state or his actions, and in the published APK there's no LLM at all, so he
> talks with his own offline lines.

---

## "Does it need internet?"

> No. The app is local-first: everything runs on the phone, and the save is one JSON file. Two things
> can use the network and both are optional in Settings: real weather (approximate city from your IP)
> and the links he opens for you (YouTube, Google, apps, maps).

---

## "Is the AI chat working in the download?"

> Not the online one. The published APK ships with no API keys and no proxy URL, so chat runs on his
> own rule-based brain. Online wording needs the small proxy in the repo (`server/`, a Cloudflare
> Worker that holds the keys) — you deploy it, set `PIPO_PROXY_URL`, and build. That's deliberate:
> keys inside an APK can be extracted, so none are shipped.

---

## "Free? Ads? In-app purchases?"

> Free, MIT licensed. No ads, no IAP, no subscription, no account, no telemetry.

---

## "Why isn't it on the Play Store?"

> Because it isn't finished enough for me to want to maintain a store listing and the compliance work
> that comes with it (data-safety declarations, the notification-listener justification, the AI
> disclosure). Right now it's a signed APK on GitHub. A Play release is the plan, not a promise.

**Do not say** "Google rejected it" — that is not what happened.

---

## "Play Protect warned me / blocked the install. Is this malware?"

> No. It's a signed release build — certificate `CN=Pipo`, SHA-256 `6ff4c9b3…adafaf7`, not the debug
> key, not debuggable. Play Protect shows that prompt for apps installed from a browser that aren't
> distributed by Play, and it is extra cautious with apps that can ask for notification access. The
> prompt is Google's friction for sideloading, not a detection. You can verify the file you downloaded
> hashes to `911b61a5…3526` and read the full audit in `security/play-protect-investigation/`.

**If someone insists it is flagged as malware:** ask for the exact wording and the scanner name. Do
not argue; a named malware family from several engines would be new information and worth
investigating, and saying so is more credible than denying it.

---

## "What permissions does it want, and why?"

> 13 declared. The core app needs almost none of them. The sensitive ones are special accesses you
> grant in Android's own settings, and each is off until you turn it on:
> - **Microphone** — only while you hold the mic button, and only if you switch on the optional
>   "Pipo Voice" wake word (an offline model you download once).
> - **Notification access** — so he can react to "you got a message" moments. It reduces each
>   notification to (app, kind) — message, photo, reel — and never stores or sends the text or the
>   sender. He never opens, replies to or dismisses anything.
> - **Device admin** — one policy only, `force-lock`, so "Pipo, lock" can lock the screen.
> - **Display over other apps** — only so he can appear when you call him from another app.
> - **Modify system settings / Do Not Disturb access** — brightness, auto-rotate, ringer, only when
>   you ask.
> There is no accessibility service, no contacts, no SMS, no location, no camera permission, and no
> storage permission.

---

## "What leaves my phone?"

> Only what you ask for: an optional chat message and a short summary of his day when you use online
> AI (through your own proxy), one photo you choose for "Pipo, look!", your approximate city for
> weather, and the searches you ask him to open. Never your notifications' content, contacts, files
> or recordings. There is no analytics SDK in the APK.

---

## "What phones does it run on?"

> Android 8.0+ on ARM phones (arm64 or armeabi-v7a). The release build excludes x86, which is only
> for emulators. It was tested on a Galaxy S23 (Android 15) — see `DEVICE_TEST_REPORT.md`.

---

## "iOS?"

> No. It's Kotlin + Jetpack Compose, and the rendering is a Compose Canvas. There's no iOS build and
> I'm not going to pretend a port is around the corner.

---

## "Can I build it myself?"

> Yes — `git clone`, then `.\gradlew.bat assembleDebug` with JDK 17 and the Android SDK. Debug builds
> need no keys. Release builds need a `keystore.properties` pointing at your own keystore; without it
> the release build now fails on purpose instead of falling back to the debug key.

---

## "What's it built with?"

> Kotlin, Jetpack Compose (no game engine, no OpenGL, no Unity), kotlinx.serialization, WorkManager,
> Vosk for the optional offline wake word, and Android's TTS. Every visual is procedural Compose
> Canvas drawing with a small 2.5D projection and depth sorting.

---

## "Why does he sound like that?"

> Android has no child voices, and simply raising the pitch of an adult voice sounds wrong because
> pitch and formants move together in a real child's vocal tract. So each line is synthesised slowly
> with raised pitch and played back at 1.45× through AudioTrack, which scales both. It lands around
> 260–340 Hz — toddler range.

---

## "Is it for kids?"

> It's a character app that happens to be family-friendly, but it isn't designed or marketed as a
> children's product, and I'd rather not put it in front of young kids as an "AI friend" without the
> compliance work that deserves.

---

## "Does it run in the background? Battery?"

> No continuous background process. There's one hourly WorkManager tick for the "he did something
> while you were away" logic, plus the optional wake-word foreground service, which shows a persistent
> notification and only listens while the screen is on and unlocked. If you never enable that, the app
> costs nothing when closed.

---

## "Will I lose my save if I update?"

> No, as long as the APK you install is signed with the same key — the next release keeps the same
> signing identity. The save format is versioned and migrates itself (the old file is copied aside
> first). Uninstalling does delete it, because it lives in the app's private storage.

---

## "Why is it 26 MB?"

> Mostly the offline wake-word library and its native code for two CPU architectures. Without that
> optional feature it would be a fraction of the size.

---

## "Is the web version the same thing?"

> No. `aspire488.github.io/Pipo/` is a Three.js room you can walk into — same character, same idea,
> different build, runs in the browser with no install and no account. `…/classic/` is the original 2D
> site. The Android app is the deeper simulation.

---

## "Can I contribute / can I use the code?"

> MIT, so yes to both. Issues with a reproducible description are more useful than PRs at this stage,
> because the behaviour engine has a lot of invariants under test.

---

## "How long did it take?"

> Do not guess. Answer with the real number or say you'd rather not put a number on it. A wrong
> timeline is the easiest thing for a hostile reader to catch.

---

## "Why 'Nib'?"

> It's the name of the small robot pet. In the fiction Pipo named it, badly, and it stuck.

---

## "What's the mystery?"

> There's a slow background thread that starts after about a week of ordinary life — a note in his
> handwriting, a reflection that doesn't quite match. No quest markers, no popups, no explanation
> from me. If someone asks for spoilers, offer to DM rather than posting them in the thread.
