# Play Protect appeal — draft

**Status: DRAFT. NOT SUBMITTED.** Nothing has been sent to Google. This file exists so the appeal can
be filed in one pass, with the evidence already assembled.

- Appeal form: <https://support.google.com/googleplay/android-developer/contact/protectappeals>
- Guidance page: <https://support.google.com/googleplay/android-developer/answer/2992033>
  ("If you believe the Google Play app verifier incorrectly blocks your app or warns users about
  installing your app, click **File an appeal** to appeal the classification.")
- Language: Google states it can **only respond to appeals in English**.

---

## 1. What is actually known, and what is not

**Known (verified in `security/play-protect-investigation/`):**

- `Pipo.apk` v1.0 (`com.pipo.robot`) is distributed only as a GitHub Release asset, not through Play.
- Installation from a browser was blocked by Play Protect's install-time app-verification gate.
- The same APK was later reported clean by a Play Protect scan and by Xiaomi Security's scanner.
- The APK is correctly release-signed (`CN=Pipo, O=Pipo`, RSA-4096, cert SHA-256
  `6ff4c9b36027408e7f4de47e2e98b1f7f1ec66a0b84abf52b07f3301aadafaf7`), `debuggable=false`, v2 scheme.
- It contains no dropper, no dynamic code loading, no `Runtime.exec`, no accessibility service, no SMS/
  contacts/location/camera access, no unprotected exported components, and no embedded API keys.
- It declares a `NotificationListenerService` that is **off by default** and, when enabled, reduces
  each notification to (app, kind) — it never stores or transmits notification text or sender.

**Not known — do not assert:**

- **The exact wording of the original Play Protect dialog was never captured.** No screenshot, no
  text, no Play Protect version, no timestamp.
- **No device was available during this investigation** (`adb devices` empty), so the block was not
  reproduced.
- The mechanism is *inferred* from Google's own documentation plus the APK's documented trigger
  conditions. It is **not** a confirmed diagnosis of that specific dialog.

An appeal that states the mechanism as fact, without the dialog text, is weaker than one that says
plainly what was observed. The draft below is written to survive that gap.

---

## 2. Before you submit — fill these in

Replace every `[…]` below. If a value cannot be obtained, delete the sentence rather than guess.

| Field | Value |
|---|---|
| Device model | `[e.g. Xiaomi 14 / Galaxy S23]` |
| Android version | `[e.g. Android 15, build …]` |
| Play Protect / Play Store version | `[Settings → Play Store → About]` |
| Date and time of the block | `[YYYY-MM-DD HH:MM timezone]` |
| How the APK was opened | `[Chrome download / Files app / other]` |
| Exact dialog text (verbatim) | `[paste it — this is the single most valuable item]` |
| Screenshot of the dialog | `[filename]` |
| Did the install succeed after the prompt? | `[yes / no / only after pausing Play Protect]` |
| Later scan result and date | `[Play Protect scan: …; Xiaomi Security scan: …]` |
| Google Play developer account | `[account email, if one exists]` |

**Also prepare (attach or link):**

1. The exact APK that was blocked — `dist\Pipo.apk`, SHA-256
   `911b61a523da2b0c91c747fdf87fcbfcd318b6bde458385b3bd69c2c121e3526`.
2. A public download URL for that same file:
   <https://github.com/aspire488/Pipo/releases/download/v1.0/Pipo.apk>
3. Public source: <https://github.com/aspire488/Pipo> (MIT).
4. The in-app explanation of notification access (README, "Noticing your notifications") and the
   privacy policy draft in `docs/PRIVACY_POLICY.md` — **host it publicly first**; a local file is not
   evidence Google can read.
5. The audit folder `security/play-protect-investigation/` — if a public link is wanted, publish it
   with the repo rather than attaching ten markdown files.

---

## 3. Draft appeal text

> **Subject:** Appeal of Play Protect app-verification block — Pipo (`com.pipo.robot`), version 1.0
>
> Hello,
>
> I am the developer of Pipo, a free Android app distributed as a signed APK from a public GitHub
> release. I am appealing an install-time block by Google Play's app-verification feature.
>
> **What happened.** When the APK was opened from a browser download, Play Protect blocked the
> installation. `[Insert the exact dialog text here, verbatim.]` `[Insert date, device model and
> Android version.]` After the prompt, the same APK scanned clean in Play Protect and in a
> third-party scanner, and it installed and ran normally.
>
> **The app.** Pipo is a character simulation: a small robot with traits, moods, memories and
> everyday activities, drawn procedurally on-device. It has no account, no analytics, no advertising
> and no in-app purchases. It does not download or execute code at runtime, has no accessibility
> service, and requests no access to SMS, contacts, location, camera or files. The source is public
> under the MIT licence at https://github.com/aspire488/Pipo.
>
> **The APK in question.**
> - Package: `com.pipo.robot`, versionName 1.0, versionCode 1
> - SHA-256: `911b61a523da2b0c91c747fdf87fcbfcd318b6bde458385b3bd69c2c121e3526`
> - Download: https://github.com/aspire488/Pipo/releases/download/v1.0/Pipo.apk
> - Signed with a project-owned release key, certificate `CN=Pipo, O=Pipo`, certificate SHA-256
>   `6ff4c9b36027408e7f4de47e2e98b1f7f1ec66a0b84abf52b07f3301aadafaf7`; APK Signature Scheme v2;
>   `debuggable=false`. It is not a debug build.
>
> **Why it declares notification access.** Pipo can react when a message arrives while he is on
> screen ("someone sent you a reel"). This is optional, off by default, and switched on by the user in
> Android's own settings. The listener converts each notification to an app name and a coarse kind
> only (message, photo, reel). It never stores, logs or transmits notification text or the sender, and
> it never opens, replies to or dismisses a notification. The feature is described in the app's
> README and privacy policy, and can be left off with no loss of core functionality.
>
> **What I am asking.** A review of this classification so that the app can be installed from its
> official release page without the verification block. I am not asking for any exemption from
> scanning, and I am not asking users to disable Play Protect. If the classification is correct, I
> would be grateful for the specific reason so I can fix it.
>
> Thank you,
> `[Name]`, developer of Pipo
> `[Contact email]`

---

## 4. What not to put in the appeal

- **Do not invent the dialog text.** If it was not captured, say so and describe only what was seen.
- Do not claim Pipo "has been verified as safe by Google" — a clean scan is not a verification.
- Do not ask for a whitelist, an allow-list entry, or a bypass. Google's own guidance is that the
  appeal reviews the *classification*.
- Do not argue that the app is small or that it is unfair. State facts and the ask.
- Do not mention the plan to publish on Play as leverage, unless you actually have a listing in
  progress; it invites "publish through Play" as the answer.
- Do not attach unrelated material (screenshots of the app, promotional videos). Evidence only.

---

## 5. Realistic expectations

- An appeal can change the classification of a known binary. It does not create reputation, and a new
  build may be evaluated again.
- With 4 lifetime downloads there is almost no reputation signal, so a positive outcome is not
  guaranteed. The durable fix is a Play Store listing, where a Play-verified app is not subject to the
  "unverified app from a browser" gate at all.
- If the appeal is declined, the honest user-facing guidance stays what `README.md` already says:
  sideloading prompts, and the user consciously allows it. Do not tell anyone to disable Play Protect.

---

## 6. Follow-up, once submitted

1. Record the submission date, the reference number if one is given, and a copy of the text sent.
2. Add the outcome to `security/play-protect-investigation/` as a new dated file — **do not rewrite
   the existing reports**, they are the baseline this appeal was built from.
3. If the response names a specific behaviour, treat that as new information and verify it against the
   APK before changing any code.
