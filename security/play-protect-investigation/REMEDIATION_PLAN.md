# Remediation Plan — Pipo

**Policy applied:** no production code was changed during this investigation. Everything below is a
*proposal*, ordered by severity and by cost. Only the smallest necessary change is proposed for each finding.

**Severity scale**

| Level | Meaning |
|---|---|
| **P0** | Confirmed malicious or critical vulnerability — stop distribution |
| **P1** | Significant security issue |
| **P2** | Release-configuration or signing issue |
| **P3** | Potential heuristic or reputation issue |
| **P4** | Informational |

---

## 1. Findings and proposed actions

| ID | Sev | Finding | Action | Effort |
|---|---|---|---|---|
| R1 | **P1** | Live Gemini/Groq keys compiled into **debug** builds | Rotate keys; stop injecting into debug; add release-APK secret assertion | 30 min |
| R2 | **P2** | Release silently falls back to debug signing | Replace fallback with a hard failure | 1 line |
| R3 | **P2** | `versionCode` still `1` while `main` is far ahead of `v1.0` | Bump `versionCode`/`versionName` before any new release | 2 lines |
| R4 | **P2** | `lintRelease` fails on a false positive | Type the `bridge` variable explicitly | 1 line |
| R5 | **P3** | Install-time Play Protect gate / reputation | File the Google appeal; pursue Play Store distribution | Process |
| R6 | **P3** | High-signal permission combination (optional features) | *Optional:* make overlay / DND / write-settings features removable | Medium |
| R7 | **P4** | No v3 signature scheme | Enable v3 signing | 1 line |
| R8 | **P4** | Stale `docs/PLAY_STORE_CHECKLIST.md` | Correct the signing item | Docs |
| R9 | **P4** | No dependency verification / SCA | Enable Dependabot + Gradle verification metadata | Small |
| R10 | **P4** | No certificate pinning for the future proxy | Pin the proxy host **if** a proxy token is ever shipped | Later |

**There is no P0 finding.** Distribution does not need to be halted.

---

## 2. R1 — Rotate keys and stop shipping them in debug builds (P1)

**Why:** `app/build.gradle.kts:24-31` injects `GEMINI_API_KEY`/`GROQ_API_KEY` into **every** build type. Only
the `release` block blanks them (lines 56-57). The current `debug` BuildConfig on this machine contains live
values, and `docs/PLAY_STORE_CHECKLIST.md:8` already flags this as outstanding.

**Scope:** debug artifacts only. The **public release APK is clean** — verified 0 key patterns in 21.5 MB of
DEX.

**Steps (in order):**

1. **Rotate both keys** in Google AI Studio and the Groq console. Treat every previously built debug APK as
   a leak. (Do this first — it is independent of any code change.)
2. Stop injecting provider keys into debug. Minimal change in `app/build.gradle.kts` — move the key fields out
   of `defaultConfig` into the `debug` block **guarded by an explicit opt-in**, or simplest: blank them in
   `debug` as well and use the proxy for all networked AI testing.

   ```kotlin
   // defaultConfig: remove GEMINI_API_KEY / GROQ_API_KEY injection
   buildTypes {
       debug {
           // Never compile provider keys into a distributable artifact.
           buildConfigField("String", "GEMINI_API_KEY", "\"\"")
           buildConfigField("String", "GROQ_API_KEY", "\"\"")
       }
   }
   ```

   (If on-device AI testing genuinely needs keys, use `local.properties`-driven **runtime** injection via a
   git-ignored file read at debug time, not `buildConfigField`.)

3. Add a CI guard so this cannot regress:

   ```yaml
   - name: Assert release APK carries no provider keys
     run: |
       ./gradlew assembleRelease
       APK=app/build/outputs/apk/release/app-release.apk
       COUNT=$(unzip -p "$APK" 'classes*.dex' | strings | grep -cE 'AIza[0-9A-Za-z_-]{35}|gsk_[0-9A-Za-z]{40,}' || true)
       test "$COUNT" -eq 0 || { echo "Secret found in release APK"; exit 1; }
   ```

   (`docs/PLAY_STORE_CHECKLIST.md:6-7` already describes this check; it is just not automated.)

**Do not** put the keys back into `defaultConfig` "temporarily".

---

## 3. R2 — Make release signing fail loudly (P2)

**Why:** `app/build.gradle.kts:62`

```kotlin
signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
```

Any build without `keystore.properties` silently produces a **debug-signed** "release" APK. This did not
affect v1.0 (verified release-signed), but it is one missing file away from shipping a debug-signed build that
cannot be upgraded over and that install-reputation systems treat poorly.

**Minimal fix:**

```kotlin
signingConfig = signingConfigs.findByName("release")
    ?: error("Release signing key missing: add keystore.properties next to settings.gradle.kts")
```

**Note:** this will make `assembleRelease` fail in CI — which is correct, because CI should not be producing
release APKs without a key. If CI must build releases, inject the keystore from GitHub Actions secrets at
runtime and never commit it.

---

## 4. R3 — Bump the version before the next release (P2)

**Why:** the distributed APK and the current `main` both report `versionCode = 1` / `versionName = "1.0"`,
while `main` is many commits ahead (`c2fc5ab`, 2026-11-09, vs release 2026-10-04) and now includes the 3D
`assets/stage/**` world. Publishing another `versionCode 1` build would be ambiguous for users and would be
rejected by any store.

**Fix:** in `app/build.gradle.kts`:

```kotlin
versionCode = 2
versionName = "1.1"
```

(Exact numbers are a product decision.)

---

## 5. R4 — Fix the lint false positive (P2, hygiene)

**Why:** `gradlew lintRelease` fails with 1 error:

```
Stage3D.kt:68: Error: None of the methods in the added interface (T) have been
annotated with @android.webkit.JavascriptInterface [JavascriptInterface]
```

**This is a false positive.** All four bridge methods carry `@JavascriptInterface`
(`Stage3D.kt:135-139`); lint reports the type as `T` because `remember { StageBridge(...) }` returns the
generic type parameter.

**Minimal fix** (no runtime behaviour change):

```kotlin
val bridge: StageBridge = remember { StageBridge(...) }
```

**Do not** suppress the check with `@SuppressLint` — fixing the type is strictly better and keeps the lint
signal meaningful.

---

## 6. R5 — Address the Play Protect gate legitimately (P3)

**Why:** the evidence points to Play Protect's **install-time app-verification / financial-fraud gate** for
unverified apps from higher-risk sources that request notification access. That is a reputation/verification
outcome, not a defect. See `PLAY_PROTECT_ANALYSIS.md`.

**Actions, in order of value:**

1. **File the appeal** (free, legitimate, documented):
   <https://support.google.com/googleplay/android-developer/contact/protectappeals>
   Reference the appeal guidance at
   <https://support.google.com/googleplay/android-developer/answer/2992033>.
   Include: package name, the signing certificate SHA-256 (`6ff4c9b3…adafaf7`), the APK SHA-256
   (`911b61a5…3526`), why notification access is needed, and that it is opt-in and off by default.
2. **Publish on Google Play.** This is the only durable fix: a Play-verified app is not subject to the
   "unverified app from a browser" gate. `docs/PLAY_STORE_CHECKLIST.md` already lists the prerequisites.
3. **Keep the README honest.** It already tells users Play Protect will warn — keep that, it sets correct
   expectations and avoids the impression of a defect.

**Do not:** remove the notification listener purely to pass a heuristic, add misleading declarations, obfuscate,
or attempt to evade the verifier. Those are policy violations and would poison a future appeal.

---

## 7. R6 — Optional heuristic reduction (P3)

Only if you want to reduce the static risk profile, and only if the features are expendable:

- **Overlay (`SYSTEM_ALERT_WINDOW`)** — the code already supports a notification-tap fallback when the overlay
  is not granted (`PipoVoiceService.bringUp` → `PipoPopUp`). Dropping the overlay path removes one of the
  highest-signal permissions at low functional cost.
- **`ACCESS_NOTIFICATION_POLICY` (DND)** — lowest-value special access; removable.
- **`WRITE_SETTINGS` (brightness/rotate)** — removable if those commands are not important.

**Explicitly do not remove:**
- the **notification listener** (it is the feature, and is properly disclosed and opt-in);
- the **device admin** (it declares only `force-lock`, and is the documented mechanism for "Pipo, lock");
- **microphone** (core to the product).

Removing legitimate features to appease a speculative warning is the wrong trade, and R5 is the correct path.

---

## 8. R7 — Enable signature scheme v3 (P4)

```kotlin
signingConfigs {
    create("release") {
        // ...
        enableV3Signing = true
    }
}
```

v3 adds proof-of-rotation lineage, enabling a future key rotation without breaking upgrades. Free, no
behavioural change. Not related to the incident.

---

## 9. R8 — Correct the stale checklist (P4)

`docs/PLAY_STORE_CHECKLIST.md:9` says *"Release currently uses the debug key (`build.gradle.kts`)"*. This is no
longer true — commit `c9965ce` added release signing and the shipped APK is release-signed. Update it to:

> Release is signed with the project key (`keystore.properties` → `D:/pipo/keys/pipo-release.jks`, alias
> `pipo`). The build fails if the key is missing.

Also update the test count (the doc says 68; the suite now has **266**).

---

## 10. R9 / R10 — Longer-term hygiene (P4)

- **Dependabot** for Gradle + **Gradle dependency verification** (`gradle/verification-metadata.xml`) for
  supply-chain integrity.
- **Certificate pinning** for the AI proxy host *only if* a proxy token is ever shipped in the APK. The current
  release ships an empty proxy config, so there is nothing to protect yet.
- Consider a **network security config** if you later need to restrict trust anchors for the proxy.

---

## 11. What should *not* be done

| Tempting action | Why not |
|---|---|
| Rebuild with `isMinifyEnabled = true` "to hide from scanners" | Obfuscation to evade analysis is a policy violation and reduces your own auditability |
| Remove the notification listener to pass a heuristic | Destroys a legitimate, disclosed feature; does not fix reputation |
| Pause/disable Play Protect as a "fix" | Masks the symptom; the gate will fire for every user |
| Add fake "verified by Google" text or metadata | Deceptive; grounds for removal |
| Rotate the signing key to "start fresh" | Breaks upgrade continuity permanently for existing installs |
| Publish a new release now | `versionCode` is stale and R1 (key rotation) is outstanding |

---

## 12. Suggested order of execution

1. **R1 step 1** — rotate the API keys. (Independent, do immediately.)
2. **R5 step 1** — file the Play Protect appeal. (Long lead time; start early.)
3. **R2 + R4 + R3** — one small build-config commit: hard-fail signing, type the bridge variable, bump version.
4. **R1 steps 2–3** — stop injecting keys into debug; add the CI secret assertion.
5. **R8** — fix the docs.
6. **R7, R9** — signing v3 and dependency hygiene.
7. **R6** — only if you decide the optional features are expendable.
8. Re-run `testDebugUnitTest` + `lintRelease` + a fresh `assembleRelease`, then verify the new APK with
   `apksigner verify --print-certs` before publishing.

---

## 13. Verification for any future release

Before publishing, confirm on the actual artifact:

```powershell
$apk = "app\build\outputs\apk\release\app-release.apk"
apksigner verify --print-certs $apk                 # expect CN=Pipo, 6ff4c9b3…
aapt2 dump badging $apk | Select-String "package|targetSdk|native-code"
aapt2 dump xmltree --file AndroidManifest.xml $apk | Select-String "debuggable"   # expect nothing
unzip -p $apk 'classes*.dex' | strings | Select-String "AIza|gsk_"                # expect nothing
Get-FileHash -Algorithm SHA256 $apk
```

Publish the SHA-256 alongside the release so users can verify the download — this also helps reputation, because
a hash that matches a previously-scanned file inherits its verdict.
