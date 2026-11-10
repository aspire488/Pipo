# Static Security Audit — Pipo

**Target:** `com.pipo.robot` v1.0, SHA-256 `911b61a5…3526`, plus the source tree at the released commit
(`c9965ce`) and the current working tree.
**Method:** binary manifest analysis of the shipped APK, DEX string scanning, and manual source review of
every security-relevant subsystem. No dynamic instrumentation was used.
**Rule applied:** ordinary Android functionality is not classified as malware. A finding is only escalated
when there is a concrete mechanism.

---

## 0. Findings index

| ID | Severity | Title | Exploitable | Heuristic contributor |
|---|---|---|---|---|
| F1 | **P2** | Release build silently falls back to debug signing | No (latent) | Indirect (would be, if triggered) |
| F2 | **P1** (dev process) | Live Gemini/Groq API keys compiled into **debug** builds | Yes, if a debug APK leaks | No (release APK clean) |
| F3 | **P3** | High-signal capability combination (listener + device admin + overlay + mic) | No | **Yes — strongest candidate** |
| F4 | P3 | `SYSTEM_ALERT_WINDOW` overlay capability | No | Yes |
| F5 | P4 | WebView JavaScript bridge | No (mitigated) | Minor |
| F6 | P4 | Runtime download of a native-model archive | No (pinned + capped) | Minor |
| F7 | P4 | Exported `MainActivity` accepts command extras | No (token-gated) | Minor |
| F8 | P4 | No v3 signature / no rotation lineage | No | No |
| F9 | P4 | No code shrinking; 21 MB readable DEX | No | No (helps analysis) |
| F10 | P4 | Stale docs claim release uses the debug key | No | No |
| F11 | P4 | Distributed release has empty AI proxy config | No | No |
| F12 | P4 | No certificate pinning / no network security config | No | No |

**No P0 finding. No evidence of malware. No unprotected exported component. No dynamic code loading.
No accessibility service. No hardcoded secret in the shipped APK.**

---

## F1 — P2 — Silent debug-key fallback in release signing

**Location:** `app/build.gradle.kts:62`

```kotlin
signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
```

**Evidence:** verified by reading the build script; the shipped APK is *not* affected (see `SIGNING_AUDIT.md`).

**Exploitable:** no. **Risk:** a build on any machine without `keystore.properties` (CI, a fresh clone, a
teammate's laptop) produces a "release" APK signed with a per-machine debug key. That artifact cannot upgrade
over a properly signed install, and debug-signed apps are treated less favourably by install-time reputation
systems.

**Heuristic contribution:** potentially high *if it ever happens*, because a debug-signed APK masquerading as a
release is exactly the profile of a repackaged/tampered app. It did **not** happen for v1.0.

**Minimal remediation:** replace the Elvis fallback with a hard failure (one line). See `REMEDIATION_PLAN.md` R1.

---

## F2 — P1 (development process) — live API keys in debug builds

**Location:** `app/build.gradle.kts:24-31` reads `GEMINI_API_KEY` / `GROQ_API_KEY` from `local.properties`
and injects them via `buildConfigField` for **all** build types. The `release` block (lines 56-57) blanks
them, but the `debug` block does not.

**Evidence:** the generated debug BuildConfig on this machine contains non-empty, live-looking values:

```
app/build/generated/source/buildConfig/debug/com/pipo/robot/BuildConfig.java
  public static final String GEMINI_API_KEY = "<redacted — non-empty>";
  public static final String GROQ_API_KEY   = "<redacted — non-empty>";
```

and `app/build/outputs/apk/debug/app-debug.apk` (SHA-256 `064c3b55…f29b`) embeds them.

The **release** APK was independently verified clean: 0 matches for `AIza…` and `gsk_…` in 21.5 MB of DEX, and
the release BuildConfig shows empty strings (`APK_FORENSICS.md` §8).

**Exploitable:** yes — anyone holding a debug APK can extract and use the keys. **Scope: debug artifacts only.**
The publicly distributed APK is not affected.

**Already known to the project:** `docs/PLAY_STORE_CHECKLIST.md:8` says *"Rotate the Gemini and Groq keys. The
current ones have been compiled into debug APKs on test phones."* This audit independently confirms that
statement is still true and still outstanding.

**Heuristic contribution:** none for Play Protect (the release APK is clean).

**Minimal remediation:** rotate both keys; stop injecting keys into `debug` (or gate them behind a
`local.properties`-only dev flag); add a CI assertion that `assembleRelease` output contains zero key matches.
See `REMEDIATION_PLAN.md` R2.

> This report deliberately does not reproduce the key values. Treat any debug APK that left the build machine as
> a credential-leak event.

---

## F3 — P3 — High-signal capability combination

**Location (all opt-in, all correctly gated):**

| Capability | File | Gate |
|---|---|---|
| Notification reading | `notify/PipoNotificationListener.kt:25-45` | `BIND_NOTIFICATION_LISTENER_SERVICE` + user grants Notification access |
| Device admin (force-lock only) | `lock/PipoLock.kt:24-52`, `res/xml/pipo_device_admin.xml` | Android's own activation screen |
| Overlay | manifest `SYSTEM_ALERT_WINDOW`; `voice/PipoPopUp.kt` | user grants "Display over other apps" |
| Microphone foreground service | `voice/PipoVoiceService.kt` | runtime `RECORD_AUDIO` + explicit Settings switch |

**Evidence of correct implementation:**

- The listener keeps **only** `(app, coarse kind)`. Text is read solely to classify
  (`PipoNotificationListener.kt:39-42`) and is never stored, logged, or transmitted; the debug log line
  (`:43`) prints app + kind only. `onNotificationRemoved` only drops a dedupe key. It never opens, replies to,
  or dismisses notifications.
- The device admin declares exactly one policy:
  ```xml
  <device-admin><uses-policies><force-lock /></uses-policies></device-admin>
  ```
  No `wipe-data`, no `reset-password`, no `force-lock`-plus-anything. `PipoLock.lockNow()` is the only DPM call
  that mutates state; `disengage()` removes the admin.
- The overlay is used only to show a small face bubble / open the activity on a wake phrase.

**Exploitable:** no. Every capability requires an explicit user grant through an Android-owned consent UI, and
each is off by default.

**Heuristic contribution: YES — this is the most plausible behaviour/permission contributor.** This exact
combination (notification access + overlay + device admin + mic) is the canonical Android banking-trojan /
spyware profile. A static or first-sight classifier has no behavioural history to distinguish Pipo from that
profile, and the app is a first release with 4 downloads.

**Minimal remediation:** none required for security. If a false-positive reduction is desired, the cheapest
legitimate lever is to make the overlay and DND/write-settings features removable (see `REMEDIATION_PLAN.md`
R3, optional). **Do not** remove the notification listener or device admin purely to appease a speculative
warning — they are legitimate, disclosed features.

---

## F4 — P3 — `SYSTEM_ALERT_WINDOW`

**Location:** `app/src/main/AndroidManifest.xml:15`; used by `voice/PipoPopUp.kt:73`.

**Evidence:** overlay permission is declared and the popup uses an explicit-component `PendingIntent` with
`FLAG_IMMUTABLE`.

**Exploitable:** no. Overlay abuse requires drawing deceptive UI over other apps; Pipo draws a small character
bubble and its own activity. There is no code path that renders attacker-controlled content over a third-party
app, and no WebView or remote content is involved in the overlay.

**Heuristic contribution:** yes, moderate — overlay is a well-known click-fraud/phishing signal.

**Minimal remediation:** optional; drop the overlay path and fall back to the notification-tap flow (which the
code already supports — "otherwise a notification appears to tap").

---

## F5 — P4 — WebView JavaScript bridge

**Location:** `ui/home/Stage3D.kt:56-104`, `StageBridge` at `:128-140`.

**Configuration observed:**

```kotlin
settings.javaScriptEnabled = true
settings.domStorageEnabled = true
settings.allowFileAccess = false        // file:// blocked
settings.allowContentAccess = false     // content:// blocked
addJavascriptInterface(bridge, "PipoAndroid")
shouldInterceptRequest { req -> if (req.url.host == HOST) serveAsset(...) else blocked() }
shouldOverrideUrlLoading { req -> req.url.host != HOST }   // any off-host navigation is blocked
loadUrl("https://pipo.app.local/stage/index.html")         // HOST is a fake, non-resolvable origin
```

`serveAsset` (`:113-118`) refuses anything outside `stage/` and rejects `..` traversal, so the page can only
load files that ship inside the signed APK. `StageBridge` exposes four methods taking only `String`/unit and
length-caps every argument (`:135-139`).

**Exploitable:** no realistic path. There is no remote content, no `file://` access, no `content://` access,
and no navigation away from the synthetic origin. Compromise would require modifying `assets/stage/**`, which
is covered by the APK signature.

**Heuristic contribution:** minor. `addJavascriptInterface` appears in scanner rules, but the surrounding
configuration is textbook-correct.

**Minimal remediation:** none needed. (Optional hardening: `settings.setGeolocationEnabled(false)` and
`settings.allowUniversalAccessFromFileURLs = false` are already effectively moot given `allowFileAccess=false`.)

---

## F6 — P4 — Runtime download of a native-model archive

**Location:** `voice/WakeModel.kt`

**Evidence — the controls are unusually good:**

- HTTPS only, from the model's publisher (`https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip`).
- **SHA-256 pinned** (`:23`) and verified before unpacking (`:68`); mismatch ⇒ reject and delete.
- Download capped at 60 MB (`:25`), unpacked size capped at 120 MB (`:27`), enforced by `copyCapped` (`:124-135`).
- **Zip-slip protection** in `entryTarget` (`:100-106`): canonical-path containment check plus absolute-path and
  traversal rejection; a bad entry raises `SecurityException` (`:114`).
- Extracted into app-private `filesDir/wake-model` (`:51`), never to external storage.
- Only triggered when the user switches Pipo Voice on.

**Exploitable:** no. The archive is integrity-pinned before any parsing, so a network attacker cannot substitute
content; the size caps bound zip-bomb exposure; the extraction root is app-private.

**Heuristic contribution:** minor — "downloads a zip and unpacks it" is a generic scanner trigger, but the
checksum pin materially changes the risk profile.

**Minimal remediation:** none. (Optional: ship the model in an App Bundle/asset for store builds.)

---

## F7 — P4 — Exported `MainActivity` accepts command extras

**Location:** `MainActivity.kt:44-58`; producer `voice/PipoVoiceService.kt:200-206`.

Because `MainActivity` is exported (required for `LAUNCHER`), any app can start it with extras. The app handles
this correctly:

```kotlin
val raw = i.getStringExtra(Notifier.EXTRA_ACTION)
val action = raw?.takeIf { it == "open" || it == "see" || it == "play" }      // strict allow-list
val fromListener = i.getStringExtra(EXTRA_VOICE_TOKEN) == PipoVoiceListener.handoffToken
val voice = i.getStringExtra(EXTRA_VOICE_COMMAND)?.take(200)?.takeIf { fromListener }
```

`handoffToken` is a per-process random UUID (`PipoVoiceListener.kt:42`), so a third-party app cannot forge the
voice-command path. The action path is a three-value allow-list and only affects in-app navigation.

**Exploitable:** no. Worst case an external app can make Pipo open his own home screen — no command execution.

**Heuristic contribution:** minor (an exported activity that takes "command" extras is a common scanner flag).

**Minimal remediation:** none required.

---

## F8 — P4 — Signature scheme coverage

v2 only; no v3. See `SIGNING_AUDIT.md` §6. No v3 means no proof-of-rotation lineage and no future key rotation.
Not a Play Protect trigger; a future-proofing gap.

---

## F9 — P4 — No shrinking / obfuscation

`app/build.gradle.kts:61` — `isMinifyEnabled = false`.

**Effect:** 21.5 MB of readable DEX, 161 entries. This makes the APK **easier** to analyse, not harder. It is
not an obfuscation or anti-analysis measure, and it does not "confuse automated analysis" — the opposite.

**Note:** size alone (27 MB) is not a detection signal, and ~19 MB of it is the Vosk speech model plus Compose.

---

## F10 — P4 — Stale documentation

`docs/PLAY_STORE_CHECKLIST.md:9` states *"Release currently uses the debug key (`build.gradle.kts`)"*. This is
**no longer true** — commit `c9965ce` added the release signing config, and the shipped APK is release-signed.
A reviewer reading this doc would reach the wrong conclusion.

**Minimal remediation:** update the checklist item to reflect the current (correct) state. Documentation only.

---

## F11 — P4 — Distributed release has empty AI proxy configuration

**Evidence:** release BuildConfig has `PIPO_PROXY_URL = ""`, `PIPO_PROXY_TOKEN = ""`, `GEMINI_API_KEY = ""`,
`GROQ_API_KEY = ""`. Therefore `AiEndpoints.usingProxy` is false and both provider keys are blank, so the
distributed APK runs on the offline brain only.

**Security impact:** none — this is the *safe* configuration (no secrets shipped). It is a **functional**
limitation, not a vulnerability. Recorded so it is not mistaken for tampering.

---

## F12 — P4 — No certificate pinning / no network security config

**Evidence:** no `network_security_config.xml`; `usesCleartextTraffic` unset (false at targetSdk 35). Traffic to
`alphacephei.com`, `ipwho.is`, `api.open-meteo.com`, and (when configured) the AI proxy uses the platform trust
store over HTTPS.

**Exploitable:** only by an attacker who can install a user-trusted CA — in which case pinning would help
marginally. The app transmits no credentials in the distributed build (F11).

**Minimal remediation:** none required for this incident. If the proxy token is ever shipped, pin the proxy
host.

---

## 3. Things that are explicitly *absent* (positive evidence)

Checked and **not present** anywhere in `app/src/main/java`:

| Technique | Result |
|---|---|
| Accessibility service | **None.** No `AccessibilityService`, no `BIND_ACCESSIBILITY_SERVICE`. |
| Dynamic code loading | **None.** No `DexClassLoader`, `PathClassLoader`, `InMemoryDexClassLoader`, no DEX in assets. |
| Process execution | **None.** No `Runtime.exec`, no `ProcessBuilder`, no `su`. |
| Reflection on untrusted input | **None.** Only two `Class.forName` sites (`unity/UnityBridge.kt:27,33`, `unity/UnityStage.kt:31,34`), both with **hardcoded** class names, both wrapped in `runCatching`, both for the optional Unity module that is not even in v1.0. |
| SMS / calls | **None.** No `SmsManager`, no `TelephonyManager.getDeviceId`, no call-log access. |
| Contacts / location / camera | **None.** No `ContactsContract`, no `LocationManager`, no `Camera.open`. |
| Package-install capability | **None.** No `PackageInstaller`, no `REQUEST_INSTALL_PACKAGES`. |
| External-storage writes | **None.** No `Environment.getExternalStorage*`, no `WRITE_EXTERNAL_STORAGE`. |
| World-readable files | **None.** No `MODE_WORLD_*`. |
| Insecure `PendingIntent` | **None.** All 5 use `FLAG_IMMUTABLE` with explicit components. |
| Hardcoded secrets (release) | **None.** 0 API-key patterns in 21.5 MB DEX. |
| Cleartext endpoints | **None.** Only XML namespace URIs. |
| Boot receiver owned by app code | **None.** `RECEIVE_BOOT_COMPLETED` is merged by WorkManager; its receiver is `enabled="false"`. |

---

## 4. Data handling observed

- `allowBackup=false` **and** `data_extraction_rules.xml` excludes `root`, `file`, `database`, `sharedpref`,
  `external` for both cloud backup and device transfer.
- `FileProvider` is `exported="false"` and scoped to `cache-path name="look" path="look/"` — only the one
  snapshot the user chooses to show Pipo, deleted after use.
- Notification text is classified and discarded, never persisted.
- Wake audio is recognised in memory and discarded; no recording.
- Outbound network destinations: `alphacephei.com` (model), `ipwho.is` + `api.open-meteo.com` (weather),
  the configured AI proxy / Gemini / Groq (only when configured), plus user-initiated intents to YouTube,
  Maps, browser, etc. **No C2-like endpoints, no analytics SDK, no ad SDK.**

---

## 5. Verdict

- **Evidence of malice:** none. The code does what the README says, using Android's documented APIs, with
  unusually careful gating for the sensitive capabilities.
- **Critical vulnerability (P0):** none found.
- **Significant security issue (P1):** one, and it is a **build-process** issue affecting *debug* artifacts
  (F2), not the distributed APK.
- **Suspicious implementation patterns:** none that are deceptive. The high-signal *capability set* (F3/F4) is
  legitimate but is the best available explanation for a heuristic/reputation flag.
- **Confidence:** high for the code-level conclusions (direct source + binary manifest review); medium for how
  any external classifier weights them.
