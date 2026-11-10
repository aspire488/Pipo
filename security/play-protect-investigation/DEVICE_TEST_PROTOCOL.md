# Device Test Protocol — Pipo Play Protect Reproduction

**Status:** **No device was connected during this investigation.** `adb devices` returned an empty list, so no
on-device testing was performed by the agent. Everything below is a protocol for **you** to execute manually.

**Purpose:** capture the evidence the original incident is missing — above all the **exact warning text** —
and distinguish the install-time verification gate from a malware detection.

---

## 0. Safety rules (read first)

- **Do not use a personal or production device.** Use a spare/test device, or a device you are willing to
  factory-reset. If you must use your main phone, get explicit approval from yourself as the owner and accept
  that installing/uninstalling changes app state.
- **Do not permanently disable Play Protect.** The protocol never asks you to turn it off. If the goal is to
  capture the block, you stop at the warning and read it.
- **Do not install anything you have not hashed.** Verify SHA-256 first (§2).
- **This APK has been statically reviewed and found non-malicious** (`STATIC_SECURITY_AUDIT.md`), so installing
  it on a test device is a reasonable risk. That conclusion is not a substitute for the hash check.
- An **x86/x86_64 emulator will not work** — the APK ships only `arm64-v8a` and `armeabi-v7a` native libraries
  and will fail with `INSTALL_FAILED_NO_MATCHING_ABIS`. Use a physical ARM device or an **arm64** emulator image.

---

## 1. Record the device baseline

Capture these **before** touching the APK:

| Field | How |
|---|---|
| Device model | Settings → About phone |
| Android version / API level | Settings → About phone → Android version |
| Security patch level | Settings → About phone → Android version → Security update |
| **Google Play system update** | Settings → Security & privacy → System & updates → **Google Play system update** (note the month/year and whether an update is pending) |
| Play Protect certification | Play Store → profile → Settings → About → **Play Protect certification** |
| Play Protect scan setting | Play Store → profile → **Play Protect** → Settings → *Scan apps with Play Protect* (on/off) |
| "Improve harmful app detection" | same screen (on/off) |
| Google Play Store version | Play Store → profile → Settings → About → Play Store version |
| Xiaomi Security app version (if Xiaomi) | Security app → Settings → About |

Record via ADB as well:

```powershell
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.security_patch
adb shell getprop ro.product.model
adb shell settings get global package_verifier_enable
adb shell dumpsys package verifier
```

---

## 2. Verify the APK hash before installing

**On the PC:**

```powershell
Get-FileHash -Algorithm SHA256 .\Pipo.apk | Format-List
```

Expected:

```
911B61A523DA2B0C91C747FDF87FCBFCD318B6BDE458385B3BD69C2C121E3526
```

**On the device** (after copying to Downloads):

```powershell
adb shell sha256sum /sdcard/Download/Pipo.apk
```

If it does not match, **stop** — you are not testing the artifact this report covers.

---

## 3. Arm A — reproduce the browser-install path (the incident path)

This is the path that produced the block. It must be a **browser download**, not ADB, because the gate keys on
the install source.

1. On the test device, open the browser and go to
   `https://github.com/aspire488/Pipo/releases/tag/v1.0`, download `Pipo.apk`.
2. **Start a logcat capture on the PC before tapping install:**

   ```powershell
   adb logcat -c
   adb logcat -v time > D:\tessera\.tmp\opencode\pipo-install-armA.log
   ```

   (Leave it running in a second terminal.)
3. Open the downloaded APK from the browser's download notification (or Files app).
4. If prompted, enable **Install unknown apps** for the browser — this is the "higher risk source" condition.
5. Tap **Install**.

### What to capture at the warning (the critical step)

**Do not dismiss it yet.** Photograph or screenshot the dialog and transcribe it **verbatim**:

- [ ] **Exact title** (e.g. "Unsafe app blocked", "App scan recommended", "Play Protect", "For your safety…")
- [ ] **Exact body text** — word for word
- [ ] **Button labels** (e.g. "Install anyway" / "OK" / "Scan app" / "Cancel" / "Don't install")
- [ ] Whether it says **"unverified"**, **"unknown developer"**, **"harmful"**, **"blocked for your safety"**,
      **"financial fraud"**, or **"app scanning"**
- [ ] Whether there is a **"Scan app"** / **"Scan anyway"** option (that indicates mechanism B, not A)
- [ ] Screenshot filename + timestamp

> The exact wording is the single most valuable piece of missing evidence. It distinguishes
> "unverified app blocked" (verification gate) from "harmful app detected" (malware detection) from
> "app scan recommended" (no verdict at all).

6. **Stop here if the goal is only diagnosis.** Do not bypass the gate. If you want to continue to
   step 4–6 of the incident, use a device you accept the risk on, and prefer the on-screen
   **"Install anyway"**/**"Scan app"** option over pausing Play Protect globally.

7. If you do install, record the outcome:

   ```powershell
   adb shell pm list packages | findstr com.pipo.robot
   adb shell dumpsys package com.pipo.robot | findstr /i "versionName versionCode installerPackageName"
   ```

   `installerPackageName` should show the browser package (e.g. `com.android.chrome`), **not** `null`/`shell`.

8. Stop logcat and keep the file. Then run a manual Play Protect scan:
   Play Store → profile → **Play Protect** → **Scan** → record the result verbatim.

---

## 4. Arm B — control: ADB install (trusted path)

On a **freshly reset** device (or after uninstalling Arm A's install), install the same file via ADB:

```powershell
adb install -r .\Pipo.apk
```

Record whether any Play Protect dialog appears.

**Interpretation:**

| Arm A (browser) | Arm B (adb) | Conclusion |
|---|---|---|
| Blocked | Installs silently | Confirms the **install-source** component of the gate → reputation/verification, not malware |
| Blocked | Also blocked | Suggests a permission/reputation gate independent of source → still not malware, but stronger signal |
| Not blocked | Installs silently | The original block may have been a transient/reputation state; re-check definitions and Play Protect state |

**This control is what separates "sideloading reputation" from "something about the app".**

---

## 5. Arm C — capture the verifier's own verdict

After installation (either arm), if the device offers a scan:

1. Play Store → profile → **Play Protect** → **Scan**.
2. Record: timestamp, result text, whether Pipo is listed, and any "harmful app" notice.
3. Xiaomi: Security app → **Scan** (or App scan) → record result text and timestamp.

Also capture the verifier log:

```powershell
adb logcat -d -b all | Select-String -Pattern "verifier|Verifier|PlayProtect|protect|harmful|PackageInstaller" -CaseSensitive:$false
```

---

## 6. Results table (fill in)

| Field | Value |
|---|---|
| Date / time | |
| Device model | |
| Android version / API | |
| Security patch | |
| Google Play system update | |
| Play Store version | |
| Play Protect scan setting | |
| APK SHA-256 (PC) | |
| APK SHA-256 (device) | |
| Arm A install source | |
| **Arm A exact warning title** | |
| **Arm A exact warning body** | |
| **Arm A button labels** | |
| Arm A outcome | |
| Arm B (adb) outcome | |
| Post-install Play Protect scan | |
| Xiaomi scan result | |
| `installerPackageName` | |
| Log file paths | |

---

## 7. How to interpret what you capture

| Captured wording | Likely mechanism | Meaning |
|---|---|---|
| "unverified", "unknown developer", "not from the Play Store", "blocked for your safety" | **A — app verification** | Reputation/verification. Expected for this app. Appeal or publish on Play. |
| "can read your notifications", "financial fraud", "sensitive permissions" | **A — fraud-prevention gate** | Exactly the documented trigger. Strongly supports this report. Appeal. |
| "harmful", "contains malware", "unsafe app blocked" + a family name | **B — malware detection** | **Contradicts this audit.** Escalate: re-open as P0, do not distribute. |
| "Scan app recommended" / "app has not been scanned" | neither — no verdict | Just an offer to scan. Not a detection at all. |
| No dialog on Arm A | — | The original block was environmental (stale definitions, device state). |

---

## 8. Do not do

- Do not turn Play Protect off globally to "make the test pass".
- Do not install on a device holding real accounts, banking apps, or personal data.
- Do not test a build other than the hashed artifact — a different build invalidates the comparison.
- Do not upload the APK to a scanning service without explicit approval (see `PLAY_PROTECT_ANALYSIS.md` §6).
