# Pipo — final release checklist (v1.1 code state)

**Date:** 2026-11-10 · **Scope:** the three security fixes requested, plus verification and launch
preparation. Nothing was committed, pushed, published or uploaded.

This file is the summary of **code changes made in this session**. The audit itself lives in
`security/play-protect-investigation/` and was **not** modified — it is the baseline.

---

## 1. Code changes

### 1.1 Provider keys are no longer compiled into any APK

**File:** `app/build.gradle.kts` (`defaultConfig`)

| Before | After |
|---|---|
| `GEMINI_API_KEY` / `GROQ_API_KEY` read from `local.properties` / env and emitted via `buildConfigField` | Both emitted as the literal empty string `""` |

Why this shape:

- `GEMINI_API_KEY` / `GROQ_API_KEY` must still **exist** as `BuildConfig` fields, because
  `ai/AiEndpoints.kt` reads them at runtime. Removing the fields would break compilation. Empty is the
  correct state: the code path is already written to fall back when they are blank.
- Networked AI is now supported only through **your own proxy** (`server/`, a Cloudflare Worker holding
  the keys) via `PIPO_PROXY_URL` / `PIPO_PROXY_TOKEN`, which remain configurable.
- `GEMINI_MODEL` / `GROQ_MODEL` are still read — a model *name* is not a secret.

**Verification (see §2):** no `AIza` or `gsk_` string exists in either built APK's dex or
`resources.arsc`. The same scan on `dist\Pipo.apk` (v1.0) also finds none — v1.0 was already key-free
because its `local.properties` keys were never set on the release build machine; the difference is that
the build no longer *can* inject them.

### 1.2 Release signing no longer falls back to the debug keystore

**File:** `app/build.gradle.kts`

Two changes that work together:

1. `release { signingConfig = signingConfigs.findByName("release") }` — the
   `?: signingConfigs.getByName("debug")` fallback is gone. If `keystore.properties` is absent, the
   signing config is `null`.
2. A `gradle.taskGraph.whenReady` guard throws `GradleException` when a **release artifact task** is in
   the graph and no release keystore is configured:

   > `Release signing is not configured: keystore.properties is missing at the repository root. Create it with storeFile / storePassword / keyAlias / keyPassword pointing at your release keystore (see README, 'Signing'). Refusing to build a release artifact without the release identity.`

The guard is scoped deliberately. It fires only for `assembleRelease`, `bundleRelease`,
`installRelease`, and `package*Release`. It does **not** fire for `assembleDebug`, `testDebugUnitTest`
or `lintRelease`, so CI (`.github/workflows/build-apk.yml`, debug only) and lint keep working on a
machine with no keystore.

**Why this is the right failure mode:** a debug-signed APK can never be installed over a properly signed
one, and to a scanner it looks like a repackaged build. Failing loudly at build time is strictly better
than shipping an artifact that silently loses the app's identity.

**The existing signing identity was not touched.** `keystore.properties` and the `.jks` are unchanged;
the release APK is still `CN=Pipo, O=Pipo`, certificate SHA-256
`6ff4c9b36027408e7f4de47e2e98b1f7f1ec66a0b84abf52b07f3301aadafaf7` — byte-identical certificate to
v1.0, so v1.1 installs over v1.0 as an upgrade.

**Proven in both directions (see §2.3):** with `keystore.properties` temporarily hidden,
`assembleRelease` failed with exactly that message and `assembleDebug` still succeeded; the file was
restored intact afterwards.

### 1.3 Version bump

**File:** `app/build.gradle.kts` (`defaultConfig`)

| | v1.0 (published) | This tree |
|---|---|---|
| `versionCode` | `1` | `2` |
| `versionName` | `"1.0"` | `"1.1"` |

Justification: `main` is substantially ahead of the published v1.0 (the 3D web world, plus the fixes
above), and a `versionCode` bump is required for any APK to install over the published one. `applicationId`
is unchanged (`com.pipo.robot`), the signing identity is unchanged, and the package name is unchanged —
so this is an upgrade, not a second app.

**This does not publish anything.** `dist\Pipo.apk` (v1.0) and the GitHub release `v1.0` are untouched
(§4). No `v1.1` release was created.

---

## 2. Tests and verification

### 2.1 Unit tests

```
.\gradlew.bat testDebugUnitTest
```

| Metric | Result |
|---|---|
| Test classes | 39 |
| Tests | **266** |
| Failures | **0** |
| Skipped | **0** |
| Build | `BUILD SUCCESSFUL` |

### 2.2 Builds

| Command | Result |
|---|---|
| `.\gradlew.bat assembleDebug` | `BUILD SUCCESSFUL` |
| `.\gradlew.bat assembleRelease` | `BUILD SUCCESSFUL` |

**Debug APK** — `app\build\outputs\apk\debug\app-debug.apk`, 53.08 MB
(contains x86 + both ARM ABIs and the wake-word natives; not a distribution artifact).
Dex + `resources.arsc` scan for `AIza` / `gsk_`: **clean**.

**Release APK** — `app\build\outputs\apk\release\app-release.apk`, 27.9 MB

| Property | Value |
|---|---|
| `versionCode` / `versionName` | `2` / `1.1` |
| `minSdk` / `targetSdk` | `26` / `35` |
| ABIs | `arm64-v8a`, `armeabi-v7a` |
| Signer | `CN=Pipo, O=Pipo` |
| Cert SHA-256 | `6ff4c9b36027408e7f4de47e2e98b1f7f1ec66a0b84abf52b07f3301aadafaf7` |
| `debuggable` | absent (false) |
| Secret scan | **clean** — no `AIza`, no `gsk_` |
| APK SHA-256 | `b9179b903f989fb9b9aeaab842dc5eb122bc0df8c5f376b84677a70d6fde4255` |

### 2.3 The signing guard, tested both ways

| Scenario | Command | Result |
|---|---|---|
| Keystore present | `assembleRelease` | `BUILD SUCCESSFUL`, release APK signed with `CN=Pipo` |
| Keystore hidden | `assembleRelease` | **`BUILD FAILED`** in 1 s with the "Release signing is not configured" message |
| Keystore hidden | `assembleDebug` | `BUILD SUCCESSFUL` — CI and lint are unaffected |
| Restore | — | `keystore.properties` restored, all four keys present (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) |

### 2.4 Not run, and why

- **No device test.** `adb devices` returned an empty list for the whole session, so nothing was
  installed, launched or profiled. The `DEVICE_TEST_REPORT.md` claims remain those of the earlier
  Galaxy S23 session; this session adds no new device evidence.
- **`lintRelease` was not re-run as a gate.** It carries one **pre-existing** issue, unchanged by this
  work: `ui/home/Stage3D.kt:68` — `JavascriptInterface`: "None of the methods in the added interface
  (T) have been annotated with `@android.webkit.JavascriptInterface`". It is a known false-positive shape
  for a generic-typed `WebView` bridge; `assembleRelease` passes `lintVitalRelease`, which is the gate
  that actually blocks a build. Out of scope to change.
- **Instrumentation tests** (`androidTest`) require a device — see above.

---

## 3. Documentation updated to match the code

Code that contradicts its own docs is a bug in the docs. Updated:

| File | Change |
|---|---|
| `README.md` (~line 438) | "Keys come from `local.properties` and are compiled into your own build" → keys are never compiled into any APK; online chat goes through your own proxy. |
| `README.md` (~line 519) | The `local.properties` example no longer lists `GEMINI_API_KEY` / `GROQ_API_KEY`; states both APKs are key-free. |
| `README.md` (~line 567) | "Without `keystore.properties` release falls back to the debug key" → release **fails on purpose**; debug and `lintRelease` still run without a keystore. |
| `README.md` (~line 581) | "Release builds never contain your keys" → no build contains them. |
| `README.md` (~line 643) | Removed the false past-tense claim that keys "were rotated out of use" → they are exposed and still need rotating. |
| `docs/PLAY_STORE_CHECKLIST.md` items 1–3 | Item 1 is now a regression check; item 2 states rotation is still outstanding; item 3 no longer says "release currently uses the debug key" and records the real certificate and the fail-fast guard. |

`security/play-protect-investigation/` was **not** edited — those reports are the dated baseline.

---

## 4. Things deliberately left alone

| Item | State | Why |
|---|---|---|
| `dist\Pipo.apk` (v1.0) | Untouched, SHA-256 `911b61a523da2b0c91c747fdf87fcbfcd318b6bde458385b3bd69c2c121e3526` | It is what 4 people have downloaded. Replacing it would invalidate the appeal evidence. |
| GitHub release `v1.0` | Untouched | Not republished, not edited, no new release created. |
| Release keystore / `.jks` / `keystore.properties` | Untouched | The signing identity must never change. |
| Every video file | Untouched — hashes re-checked at the end of the session | Explicitly out of scope; see `launch/VIDEO_INVENTORY.md`. |
| Gemini / Groq API keys | **Not rotated** | No authorisation and no console access in this session. |
| `local.properties` | Unchanged (still contains the old `GEMINI_API_KEY`) | It is git-ignored and no longer read by the build, so it cannot leak into an artifact. Deleting it would only break the user's own local tooling. |
| ~53 pre-existing uncommitted changes | Unchanged | Not this session's work. |

Nothing was committed, staged, pushed, tagged, or uploaded anywhere.

---

## 5. Remaining blockers before this can be called "shipped"

1. **Rotate the Gemini and Groq keys.** Older debug APKs handed to test phones embedded the live keys.
   New builds cannot, but the old keys are still valid until rotated in Google AI Studio / Groq Console.
   **This requires the account owner.**
2. **No device validation of v1.1.** The release APK is built, signed and clean, but nothing was
   installed on hardware in this session. Before announcing v1.1, install it over v1.0 on a real phone
   and confirm the save migrates.
3. **Play Protect appeal is drafted, not filed** (`launch/PLAY_PROTECT_APPEAL_DRAFT.md`). It has `[…]`
   placeholders for the exact dialog text, device/Android/Play Protect versions, timestamp, and whether
   a Play Console account exists. Those cannot be recovered retroactively.
4. **No v1.1 release exists.** If v1.1 is to be published, that is a separate, explicit decision —
   and it needs items 1 and 2 done first.
5. ~~**Privacy policy is not hosted.**~~ **Resolved.** It is live at
   <https://aspire488.github.io/Pipo/privacy/> (HTTP 200, served from `web/privacy/index.html`, source
   `docs/PRIVACY_POLICY.md`). Both the appeal and any store listing can use that URL.
6. **Reddit rules must be re-read on posting day.** This machine is blocked from Reddit's rules
   endpoints (403), so the research in `launch/REDDIT_LAUNCH_PLAN.md` mixes live RSS, dated archive
   snapshots and third-party trackers. It is dated 2026-11-10, not timeless.

---

## 6. Launch kit (written, nothing posted)

All in `D:\pipo\Pipo\launch\`:

| File | What it is |
|---|---|
| `REDDIT_LAUNCH_PLAN.md` | Per-community findings, with sources and dates; positioning; risk table |
| `REDDIT_POSTS.md` | 4 ready-to-publish drafts, one per community, deliberately different text |
| `REDDIT_FAQ.md` | 25 prepared answers, every fact checked against the shipped v1.0 APK |
| `VIDEO_INVENTORY.md` | All 9 video files, hashes, what each shows, ranking for use |
| `POSTING_ORDER.md` | Sequence, timing, per-post checklists, reply policy |
| `PLAY_PROTECT_APPEAL_DRAFT.md` | Appeal text + the evidence still needed — **not submitted** |
