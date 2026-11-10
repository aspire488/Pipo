# Executive Summary — Pipo Play Protect Investigation

**Subject:** `Pipo.apk` v1.0 (`com.pipo.robot`), SHA-256 `911b61a523da2b0c91c747fdf87fcbfcd318b6bde458385b3bd69c2c121e3526`
**Question:** why did Google Play Protect block installation, yet later report the app clean (as did Xiaomi Security)?
**Investigation date:** 2026-11-10 · **Mode:** read-only inspection, local builds and tests, official documentation review. No APK was uploaded anywhere. No code, key, or release was changed.

---

## Verdict

> **This is a reputation / verification outcome, not a security defect and not a malware detection.**
>
> Play Protect's documented **install-time app-verification ("financial fraud prevention") gate** blocks
> *unverified* apps installed from *higher-risk sources such as a web browser* when they **can request
> sensitive permissions such as reading notifications**. Pipo is sideloaded from GitHub, is not on Play
> Store, has **4 total downloads**, a signing certificate created the same day as the release, and declares a
> **notification listener**. The later clean scans are a *different mechanism* — code-level malware scanning —
> and Pipo contains no malware, so they correctly pass.

**Confidence: high** on the mechanism (Google documents it explicitly; the APK matches the documented trigger
conditions). **Medium** on attributing it to this specific warning, because the exact original warning text was
never captured.

---

## The ten required answers

### 1. Is there evidence that Pipo is malicious?
**No.** High confidence.

No dropper, no dynamic code loading (`DexClassLoader`/`PathClassLoader`/`InMemoryDexClassLoader`), no
reflection on untrusted input, no `Runtime.exec`/`ProcessBuilder`, no C2 endpoints, no SMS/contacts/location
access, **no accessibility service at all**, no package-install capability, no hardcoded secrets in the release
APK, no unprotected exported components. All five `PendingIntent`s are `FLAG_IMMUTABLE` with explicit
components. The code does what the README says, using documented Android APIs.

### 2. Is the distributed APK debug-signed?
**No.** High confidence.

Signed by `CN=Pipo, O=Pipo`, RSA-4096, certificate SHA-256
`6ff4c9b36027408e7f4de47e2e98b1f7f1ec66a0b84abf52b07f3301aadafaf7` — **not** `CN=Android Debug`. The
certificate matches the project keystore `D:/pipo/keys/pipo-release.jks`, alias `pipo`. The APK is also
`debuggable=false`, `testOnly` absent, and verifies under APK Signature Scheme v2.

The **CI artifact** *is* debug-signed (`ee8a0f4a…`), because CI only runs `assembleDebug`. That is a different
artifact and was never the one users downloaded.

### 3. Is its signing identity stable?
**Yes, within the limits of the evidence.** High confidence on identity; **medium** on cross-release stability.

One project-owned key, alias `pipo`, valid 2026-10-03 → 2054-02-18, stored **outside** the git repo, with
`keystore.properties` git-ignored and no key material tracked. Rebuilding the release produced the **same**
certificate. However, only **one** release exists, so stability across releases cannot be measured — there is
nothing to compare against. The configuration is designed for stability; the risk is losing the keystore, which
would permanently break in-place upgrades.

### 4. Are any permissions unnecessary?
**No.** High confidence.

13 permissions, each mapped to a real, reachable feature. Notably **absent**: location, contacts, SMS, call log,
camera, storage, `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, accessibility. Two permissions
(`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`) are merged in by WorkManager, not authored by the app.

### 5. Are there suspicious implementation patterns?
**No deceptive patterns.** High confidence on the code; **medium** on how an external classifier weights it.

The **capability combination** is high-signal and is the best explanation for a heuristic flag:
`RECORD_AUDIO` + `FOREGROUND_SERVICE_MICROPHONE` + `NotificationListenerService` + `DeviceAdmin` +
`SYSTEM_ALERT_WINDOW` + `WRITE_SETTINGS`. That is the canonical Android spyware/banking-trojan profile.

But every capability is **opt-in, off by default, minimal, and gated by Android's own consent screens**: the
device admin declares **only** `force-lock`; the notification listener keeps **only** `(app, coarse kind)` and
never stores, logs, or transmits text; the WebView is locked to APK-internal assets with `allowFileAccess=false`
and off-host navigation blocked.

### 6. Does the APK differ materially from the latest CI build?
**Yes — but entirely explainable, and nothing security-relevant.** High confidence.

| | Public v1.0 | CI / local debug |
|---|---|---|
| Signer | `CN=Pipo` | `CN=Android Debug` |
| `debuggable` | false | **true** |
| Size | 27.4 MB | 53.7 MB |
| ABIs | 2 (ARM) | 7 (incl. x86) |

The differences are *by design* (CI builds only debug). Rebuilding `release` from the current tree reproduced
the **same certificate, package, version, permission set, and native libraries**, differing only by
(a) 20 new `assets/stage/**` files from post-v1.0 work and (b) normal non-reproducible-build mechanics.

### 7. Is a reputation-based warning plausible?
**Yes — highly plausible, and specifically documented.** High confidence.

Google's Play Protect help page states it *"may prevent an application from being installed that is unverified
and uses sensitive device permissions that are commonly targeted by scammers to commit financial fraud"*, and
that it *"analyzes apps that you try to install from higher risk sources, such as a web browser or messaging
app … if the app can request sensitive device permissions, such as the ability to read your … notifications …
Google Play Protect will block these apps from installation."*

Pipo matches: browser download, not Play-verified, **4 downloads**, first release, new certificate, and a
notification listener.

### 8. Can the original Play Protect warning be explained from available evidence?
**The mechanism, yes. The exact wording, no.** High confidence on mechanism; medium on the specific dialog.

The evidence supports the install-time verification/fraud gate (mechanism **A**), not a malware detection
(mechanism **B**). This is consistent with every observation: blocked at install, clean on the later code-level
scan, clean in Xiaomi's signature engine.

**The exact original warning text was not captured, and this report does not invent it.** `DEVICE_TEST_PROTOCOL.md`
is written to capture it in one pass.

### 9. What is the smallest corrective action?
**For the incident: none in code.** File the Play Protect appeal
(<https://support.google.com/googleplay/android-developer/contact/protectappeals>) and pursue Play Store
distribution, which removes the "unverified sideload" gate entirely.

**For the repository, two small changes:**
1. **Rotate the Gemini/Groq keys** (they are compiled into *debug* builds — already flagged as outstanding in
   `docs/PLAY_STORE_CHECKLIST.md:8`). The public APK is clean.
2. **Make release signing fail loudly** instead of silently falling back to the debug key — one line in
   `app/build.gradle.kts:62`.

### 10. Is the current public APK suitable for continued distribution?
**Yes, with caveats.** Medium-high confidence.

It is safe (no malware, no secrets, correctly release-signed, non-debuggable) and I would not recommend pulling
it. But:

- it will **keep** triggering install-time friction for new users, because it has no reputation and requests
  notification access — that is Google's intended friction, not a Pipo bug;
- `versionCode` is still `1`, so any replacement build must bump it;
- the README already tells users Play Protect will warn — keep that honest guidance.

Do **not** rebuild and re-upload a replacement under the same version code, and do not attempt to evade the gate.

---

## Evidence chain (how the artifact was verified)

1. `dist/Pipo.apk` SHA-256 = `911b61a5…3526`.
2. GitHub Releases API reports the **same** digest for the published `v1.0` asset → the local file **is** the
   public file. No download, no upload.
3. `apksigner verify --print-certs` → `CN=Pipo`, RSA-4096, v2.
4. `keytool -list` on `pipo-release.jks` → certificate SHA-256 **matches** the APK signer exactly.
5. `aapt2 dump badging/xmltree/permissions` → 13 permissions, all exported components protected, not debuggable.
6. DEX scan of 21.5 MB → **0** API-key patterns, **0** cleartext endpoints.
7. `gradlew testDebugUnitTest` → **266 tests pass** (39 classes).
8. `gradlew assembleRelease` → same cert, same permissions, same native libs; only asset drift + build
   nondeterminism.

---

## Severity summary

| Level | Count | Items |
|---|---|---|
| **P0** — critical / malicious | **0** | — |
| **P1** — significant | 1 | Live API keys in **debug** builds (public APK clean) |
| **P2** — release config / signing | 3 | Silent debug-key fallback · stale `versionCode` · lint false-positive failing `lintRelease` |
| **P3** — heuristic / reputation | 2 | Install-time Play Protect gate · high-signal permission combination |
| **P4** — informational | 4 | No v3 signature · stale docs · no SCA/dependency verification · no cert pinning |

---

## Documents in this folder

| File | Contents |
|---|---|
| `EXECUTIVE_SUMMARY.md` | This document — verdict and the ten answers |
| `APK_FORENSICS.md` | Hash, chain of custody, structure, native libs, secrets scan |
| `SIGNING_AUDIT.md` | Certificate, keystore match, schemes, the debug-fallback footgun |
| `PERMISSION_AUDIT.md` | All 13 permissions, justification, heuristic combination |
| `STATIC_SECURITY_AUDIT.md` | 12 findings with file/line evidence, plus explicit negatives |
| `DEPENDENCY_AUDIT.md` | 16 direct deps, 2 non-AndroidX libraries, no analytics/ads/crash |
| `BUILD_COMPARISON.md` | Public vs rebuilt vs debug, test/lint results, every hash difference explained |
| `PLAY_PROTECT_ANALYSIS.md` | The documented gate, official citations, trigger matrix, scanner plan |
| `DEVICE_TEST_PROTOCOL.md` | Reproducible manual protocol to capture the exact warning |
| `REMEDIATION_PLAN.md` | P0–P4 actions with minimal patches, and what *not* to do |

---

## Limitations

- **No device was connected** (`adb devices` empty). No on-device reproduction was performed.
- **The exact original warning text is unknown.** The mechanism is inferred from official documentation plus the
  APK's documented trigger conditions, not from the dialog itself.
- **No malware-scanning service was queried** and the APK was **not uploaded** (it may be redistributed by such
  services). An approval-gated VirusTotal procedure is in `PLAY_PROTECT_ANALYSIS.md` §6.
- **No `jadx`/`apktool`** available, so no full decompilation; conclusions rest on the binary manifest, DEX string
  scanning, and source review.
- **No automated SCA/CVE database** was available; this report does not claim "no CVEs".
- **Play Protect's detection engine is proprietary** and was not reproduced. No synthetic risk score is assigned.
- The rebuild used the **current working tree**, not the released commit, so hash differences are expected.
