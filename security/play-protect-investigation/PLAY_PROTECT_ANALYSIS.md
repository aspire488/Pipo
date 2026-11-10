# Play Protect Analysis — Pipo

**Question:** why was Pipo blocked by Google Play Protect at install time, yet subsequently reported
clean by both Play Protect and Xiaomi Security?

**Answer in one line:** the block is best explained by Play Protect's **install-time app-verification /
"financial fraud prevention" gate** for *unverified apps from higher-risk install sources that request
sensitive permissions such as reading notifications* — which Pipo does. The later scans are a different
mechanism (code-level malware scanning), and Pipo contains no malware, so they pass. This is a
**reputation/verification** outcome, not a malware detection.

**Confidence: high** for the mechanism, because Google documents it explicitly and the APK matches the
documented trigger conditions. **Medium** for assigning it to this specific warning, because the exact
original warning text was not captured.

---

## 1. How Play Protect actually works (official sources)

From Google Play Help, *"Use Google Play Protect to help keep your apps safe & your data private"*
(<https://support.google.com/googleplay/answer/2812853>):

> "It runs a safety check on apps from the Google Play Store before you download them."
> "It checks your device for potentially harmful apps from other sources."
> "It warns you about potentially harmful apps."
> **"It may prevent an application from being installed that is unverified and uses sensitive device
> permissions that are commonly targeted by scammers to commit financial fraud."**

And, specifically on the install-time gate:

> "To help protect you against potential financial fraud malware on Android, **Google Play Protect analyzes
> apps that you try to install from higher risk sources, such as a web browser or messaging app.** Play
> Protect determines if the app **can request sensitive device permissions, such as the ability to read your
> text messages or notifications**, or control your device through accessibility permissions. These are
> commonly used by bad actors to commit identity theft or financial fraud. **For your safety, Google Play
> Protect will block these apps from installation.**"

From Play Console Help, *"App verification"*
(<https://support.google.com/googleplay/android-developer/answer/2992033>):

> "Google Play uses an app verification feature to warn users when they install potentially harmful apps
> from the internet and third-party markets. **In some cases, this feature blocks app installations.**"

And on post-install scanning:

> "You may get a recommendation to scan an app from outside of Google Play that has never been scanned by
> Google Play Protect before. **Scanning the app will send app details to Google for a code-level
> evaluation.** A short time later, you'll receive a result letting you know if the app looks safe to install
> or if the scan determined the app is potentially harmful."

### The three distinct mechanisms

These are frequently conflated. They are separate:

| # | Mechanism | When | What it evaluates |
|---|---|---|---|
| A | **App verification / financial-fraud gate** | at install, especially from browser/messaging | app *identity/reputation* + *which sensitive permissions it can request* |
| B | **Code-level malware scan** | on install or on demand ("Scan") | actual code against malware signatures/heuristics |
| C | **Periodic device scan** | background, later | installed apps |

**A block at step A, followed by a clean result at step B, is not a contradiction — it is the expected
outcome for a legitimate app that requests sensitive permissions and has no reputation.**

---

## 2. Does Pipo match the documented trigger conditions?

| Documented condition | Pipo | Evidence |
|---|---|---|
| Installed from a "higher risk source, such as a web browser" | **Yes** | APK downloaded from `github.com/aspire488/Pipo/releases/download/v1.0/Pipo.apk` in a browser, per `README.md:58-61` |
| App is **unverified** (no Play Store identity, no established reputation) | **Yes** | not on Play Store; **4 total downloads**; first release; signing cert created the same day |
| Can request **"the ability to read your … notifications"** | **Yes** | `PipoNotificationListener` is a `NotificationListenerService` with `BIND_NOTIFICATION_LISTENER_SERVICE` |
| Can control the device via accessibility permissions | **No** | no accessibility service exists (positive finding) |
| Can read text messages | **No** | no SMS permissions |

**Pipo satisfies three of the documented trigger conditions, including the most specific one — notification
reading.** Under Google's own published description, an install-time block is the *documented* behaviour for
an app in this position, not an anomaly.

The install source matters as much as the app: the same APK installed via `adb install` (a developer/trusted
path) may not be gated the same way as one opened from a browser download. The user's report — "downloaded
and installation was attempted" — is the browser path.

---

## 3. Why the later scans said "okay"

This is the part that most often gets misread as "the first warning was wrong, so it must have been a bug."

- **Play Protect's later scan (step B) is a code-level malware evaluation.** Pipo's code contains no malware:
  no droppers, no dynamic code loading, no C2, no accessibility abuse, no SMS theft. A signature/heuristic
  scan of this code legitimately returns clean. See `STATIC_SECURITY_AUDIT.md`.
- **Xiaomi Security is primarily an antivirus/signature engine plus a local reputation check.** It evaluates
  whether the binary matches known-bad families. It does not enforce Google's install-source fraud gate. Clean
  is the correct result for a non-malicious app.
- **Neither result clears the install-time gate**, because that gate was already passed (Play Protect was
  paused). Nothing in the later scans retroactively re-evaluates the *install-source + permission* decision.

**Therefore:** "Play Protect blocked it, then Play Protect said it was fine" is internally consistent and is
**not** evidence that the block was spurious, and **not** evidence that the app is malicious. It is evidence
that two different checks ran.

The user's instinct to distrust the clean results is correct in principle — but here the clean results are
*also* correct, because the two checks answer different questions.

---

## 4. Candidate triggers — evaluated

| Candidate | Verdict | Reasoning |
|---|---|---|
| **Genuine malware** | **Ruled out** (high confidence) | Full static review: no dropper, no dynamic DEX, no reflection on untrusted input, no C2, no SMS/contact/location access, no accessibility service, no overlay phishing, no hardcoded secrets in the release APK. |
| **Debug signing** | **Ruled out** (high confidence) | APK is signed with a project-owned RSA-4096 key (`CN=Pipo`), **not** `CN=Android Debug`. Verified against the keystore. |
| **Unstable / changed signing identity** | **Ruled out** (high) | Only one release exists; keystore matches the APK exactly; cert valid to 2054. |
| **Sensitive permissions** | **CONFIRMED contributor** (high) | Notification-listener capability is explicitly named in Google's fraud-prevention description. Plus overlay, device admin, mic FGS. |
| **Suspicious API combinations** | **Plausible contributor** (medium) | `RECORD_AUDIO` + notification listener + `SYSTEM_ALERT_WINDOW` + device admin is the canonical banking-trojan profile, even though each is opt-in and minimal here. |
| **Dynamic code loading** | **Ruled out** | No `DexClassLoader`/`PathClassLoader`/`InMemoryDexClassLoader`. The only download is a checksum-pinned Vosk model archive. |
| **Accessibility / device-management abuse** | **Partly relevant** | No accessibility service at all (strong positive). A device admin **does** exist, but it declares only `force-lock`. Device admin is itself a high-signal capability. |
| **Unusual installer/source conditions** | **CONFIRMED contributor** (high) | GitHub Release + browser download + "install unknown apps" is the exact "higher risk source" path Google describes. |
| **Application behaviour** | **Not a trigger** (medium-high) | Behaviour is a character app: rendering, TTS, opt-in voice, optional AI chat, hourly WorkManager job. Nothing covert. |
| **Known false-positive pattern** | **Yes — this is one** (medium-high) | New, low-reputation, sideloaded app with notification access is a well-known false-positive shape. |

---

## 5. What can and cannot be established from local evidence

### Established (with evidence)
- The distributed APK is byte-identical to the release build and to the GitHub Release asset digest.
- It is release-signed, not debuggable, not test-only, contains no secrets, requests 13 permissions, and has
  no unprotected exported components.
- It requests notification-listener capability, overlay, and device admin — the documented high-risk set.
- It has essentially no reputation: 4 downloads, first release, certificate created the same day.
- It is distributed outside any store, via browser download.

### Not established (and not claimed)
- **The exact original warning text.** It was not captured. We do not know whether it said "unsafe", "app
  scanning recommended", "blocked for your safety", "unknown app", or the financial-fraud wording. **This
  report does not invent it.**
- **The exact Play Protect classification.** Google's verifier output is server-side and proprietary.
- Whether the block was `A` (fraud gate) or a reputation warning that merely *looked* like a block. Both are
  consistent with the observation; the fraud gate is the better fit because it *blocks* rather than warns,
  and because notification access is explicitly named.
- Whether the test device's Play Protect definitions/Google Play system update were current.

**We do not reproduce Google's detection engine, and no synthetic "risk score" is assigned here.**

---

## 6. Multi-scanner analysis — status and plan

**No malware-scanning service was queried. The APK was NOT uploaded anywhere.**

This is deliberate: VirusTotal and similar services may retain and **redistribute** uploaded files to
security researchers and commercial partners. Uploading is an irreversible disclosure of the binary.

If you want external corroboration, here is the approval-gated procedure:

1. **You approve** uploading `Pipo.apk` (SHA-256 `911b61a5…3526`) to VirusTotal.
2. **First check the hash only** — no upload:
   `https://www.virustotal.com/gui/file/911b61a523da2b0c91c747fdf87fcbfcd318b6bde458385b3bd69c2c121e3526`
   If it already exists, you get results with zero disclosure.
3. If it does not exist and you still approve, upload, then record:
   - detection ratio and the names of any engines that flag it;
   - whether flags are **generic/heuristic** (e.g. "Riskware", "PUA", "Android.Riskware.*") vs a **named
     family** (e.g. a specific trojan family). Generic riskware flags on a sideloaded app with a notification
     listener are expected and are not malware evidence;
   - the `sigma`/behaviour tabs, if any.

**Interpretation rules to apply to the results:**

| Result type | What it means |
|---|---|
| Static/signature detection by 1–3 minor engines | Usually a heuristic false positive on the permission set |
| Named malware family by ≥5 reputable engines | Escalate immediately — contradicts this audit, re-open as P0 |
| "Riskware"/"PUA"/"Not-a-virus" labels | Reputation/heuristic, consistent with this report |
| Clean everywhere | Consistent; does not by itself prove safety, and does not clear the install gate |

---

## 7. The legitimate remedy: appeal

Google provides an explicit appeal path for exactly this situation
(<https://support.google.com/googleplay/android-developer/answer/2992033>):

> "If you believe the Google Play app verifier incorrectly blocks your app or warns users about installing
> your app, click the **File an appeal** button to appeal the classification."

**Appeal form:** <https://support.google.com/googleplay/android-developer/contact/protectappeals>

Note Google's constraint: *"we can only respond to appeals in English at this time."*

**What makes a strong appeal (all of it already true for Pipo):**
- Stable, project-owned signing certificate (fingerprint `6F:F4:C9:…:FA:F7`), unchanged across releases.
- A clear, published explanation of *why* notification access is needed and that it is opt-in and off by
  default.
- Evidence that the app does not abuse the capability (classification only; no content stored or transmitted).
- A privacy policy URL and a public source repository.

**Do not** attempt to defeat the gate by removing the feature purely to pass, adding misleading declarations,
or obfuscating. Those are policy violations and would make a future appeal harder.

---

## 8. Realistic expectations

- An appeal can change the **classification** of a known binary, but new builds may be re-evaluated.
- Reputation accrues slowly. With 4 downloads, there is very little signal for any reputation system to use.
- **Publishing on Google Play is the only durable fix** for install-time friction: a Play-signed, Play-verified
  app is not subject to the "unverified app from a browser" gate at all.
- Until then, the honest, user-facing guidance is what `README.md` already says: sideloading will prompt, and
  the user must consciously allow it. That is Google's intended friction for *every* app distributed this way —
  not a Pipo-specific defect.

---

## 9. Bottom line

The observed sequence is **exactly** what Google documents for a legitimate, unverified, sideloaded app that
requests notification access:

1. Install from browser → app-verification/fraud gate blocks it (documented behaviour).
2. Gate bypassed by pausing Play Protect → install succeeds.
3. Code-level scan finds no malware → "okay".
4. Third-party AV finds no signature → "okay".

**This is a verification/reputation outcome, not a security defect in Pipo and not a malware detection.**
The correct response is the appeal process plus a Play Store path — not code changes made to appease a
heuristic.
