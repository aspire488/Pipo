# APK Forensics — Pipo

**Artifact under investigation:** `Pipo.apk`, GitHub Release `aspire488/Pipo` tag `v1.0`.
**Local copy:** `D:\pipo\Pipo\dist\Pipo.apk`
**Analysis date:** 2026-11-10
**Tools:** `apksigner`, `aapt2`, `keytool` from Android SDK build-tools 35.0.0; PowerShell ZIP reader;
direct DEX string scanning. `jadx` was **not** installed, so decompilation was not used — findings are from
manifest, resources, DEX strings, and source review of the exact same commit.

---

## 1. Identity

| Property | Value |
|---|---|
| File name | `Pipo.apk` |
| Size | 27,351,753 bytes (26.08 MiB) |
| SHA-256 | `911b61a523da2b0c91c747fdf87fcbfcd318b6bde458385b3bd69c2c121e3526` |
| SHA-1 | `609727edfc8369c9a1bb19efa5ce57fa9af6e80c` |
| MD5 | `9f114274f26ebd191315454822271079` |
| Package name | `com.pipo.robot` |
| Version name | `1.0` |
| Version code | `1` |
| minSdkVersion | `26` (Android 8.0) |
| targetSdkVersion | `35` (Android 15) |
| compileSdkVersion | `35` |
| Platform build | `15` / `35` |
| Launchable activity | `com.pipo.robot.MainActivity` |
| Label | `Pipo` |
| Native code | `arm64-v8a`, `armeabi-v7a` only |

---

## 2. Chain of custody — is this the file users actually download?

The local file was **not** assumed to be the public one. It was verified against the GitHub Releases API,
which publishes a server-computed digest for each asset.

`GET https://api.github.com/repos/aspire488/Pipo/releases/tags/v1.0` →

```json
"name": "Pipo.apk",
"size": 27351753,
"digest": "sha256:911b61a523da2b0c91c747fdf87fcbfcd318b6bde458385b3bd69c2c121e3526",
"download_count": 4,
"browser_download_url": "https://github.com/aspire488/Pipo/releases/download/v1.0/Pipo.apk"
```

Local hash:

```
911B61A523DA2B0C91C747FDF87FCBFCD318B6BDE458385B3BD69C2C121E3526  D:\pipo\Pipo\dist\Pipo.apk
```

**Result: byte-for-byte identical.** No download was required, and nothing was uploaded anywhere.

It is also **byte-identical** to the local release build output:

```
911B61A5…3526  D:\pipo\Pipo\dist\Pipo.apk
911B61A5…3526  D:\pipo\Pipo\app\build\outputs\apk\release\app-release.apk
```

**Significance:** there is a single, consistent artifact. There is no evidence of a tampered or substituted
build, and no divergence between what was built and what was published.

**Distribution facts relevant to reputation:**

- Release published `2026-10-04T01:17:28Z`.
- **Total downloads: 4.**
- Sideloaded from GitHub (not Play Store, not an OEM store).
- First and only release of the app.

---

## 3. Signature

| Property | Value |
|---|---|
| Signature schemes | **v2 only** (v1 `false`, v3 `false`, v3.1 `false`, v4 `false`) |
| Signers | 1 |
| Certificate DN | `CN=Pipo, O=Pipo` |
| Certificate SHA-256 | `6ff4c9b36027408e7f4de47e2e98b1f7f1ec66a0b84abf52b07f3301aadafaf7` |
| Key | RSA 4096-bit, `SHA384withRSA` |
| Certificate validity | 2026-10-03 → 2054-02-18 |
| Debug-signed? | **No** |
| SourceStamp | none |

Full detail and the keystore match are in `SIGNING_AUDIT.md`.

---

## 4. Build flags

Extracted from the binary manifest (`aapt2 dump xmltree --file AndroidManifest.xml`):

| Flag | Value | Assessment |
|---|---|---|
| `android:debuggable` | **absent** (false) | Correct for release |
| `android:testOnly` | absent | Correct |
| `android:allowBackup` | `false` | Correct |
| `android:dataExtractionRules` | `@xml/data_extraction_rules` — excludes root/file/database/sharedpref/external for both cloud-backup and device-transfer | Correct, stronger than default |
| `android:usesCleartextTraffic` | **not set** → defaults to `false` at targetSdk ≥ 28 | Cleartext blocked |
| `networkSecurityConfig` | none | System defaults apply: HTTPS with system trust store |
| `android:extractNativeLibs` | not set (AGP default, uncompressed libs) | Normal |

**The APK is a genuine release build, not a debug or test build.**

---

## 5. Permissions (as declared in the shipped APK)

13 entries. Two of them are **not** in the source manifest and are merged in by dependencies:

| # | Permission | Source | Protection level |
|---|---|---|---|
| 1 | `android.permission.RECORD_AUDIO` | app manifest | dangerous (runtime) |
| 2 | `android.permission.POST_NOTIFICATIONS` | app manifest | dangerous (runtime, API 33+) |
| 3 | `android.permission.FOREGROUND_SERVICE` | app manifest | normal |
| 4 | `android.permission.FOREGROUND_SERVICE_MICROPHONE` | app manifest | normal |
| 5 | `android.permission.SYSTEM_ALERT_WINDOW` | app manifest | special app-op (user toggle) |
| 6 | `android.permission.INTERNET` | app manifest | normal |
| 7 | `android.permission.ACCESS_NETWORK_STATE` | app manifest | normal |
| 8 | `com.android.alarm.permission.SET_ALARM` | app manifest | normal |
| 9 | `android.permission.WRITE_SETTINGS` | app manifest | special app-op (user toggle) |
| 10 | `android.permission.ACCESS_NOTIFICATION_POLICY` | app manifest | normal, but DND access is a user toggle |
| 11 | `android.permission.WAKE_LOCK` | **merged** (androidx.work) | normal |
| 12 | `android.permission.RECEIVE_BOOT_COMPLETED` | **merged** (androidx.work) | normal |
| 13 | `com.pipo.robot.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | **merged** (AGP, app-defined) | signature |

**Absent, and notably so:** no `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION`, no `READ_CONTACTS`, no
`READ_SMS`/`SEND_SMS`, no `READ_CALL_LOG`, no `CAMERA`, no storage permissions, no
`QUERY_ALL_PACKAGES`, no `REQUEST_INSTALL_PACKAGES`, no `BIND_ACCESSIBILITY_SERVICE`, no
`PACKAGE_USAGE_STATS`, no `READ_PHONE_STATE`, no `SYSTEM_ALERT_WINDOW` abuse beyond the declared feature.

This is a **narrow** permission set for the feature set described. Detail in `PERMISSION_AUDIT.md`.

---

## 6. Components (from the shipped, merged manifest)

| Component | Type | Exported | Protection |
|---|---|---|---|
| `com.pipo.robot.MainActivity` | activity | **true** | launcher intent-filter (unavoidable) |
| `com.pipo.robot.notify.NotificationActionReceiver` | receiver | false | — |
| `com.pipo.robot.notify.PipoNotificationListener` | service | **true** | `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` |
| `com.pipo.robot.voice.PipoVoiceService` | service | false | `foregroundServiceType=microphone` |
| `com.pipo.robot.lock.PipoDeviceAdmin` | receiver | **true** | `android.permission.BIND_DEVICE_ADMIN` |
| `androidx.core.content.FileProvider` (`.look`) | provider | false | — |
| `androidx.startup.InitializationProvider` | provider | false | — |
| `androidx.work.*` (SystemAlarmService, SystemJobService, SystemForegroundService, ForceStopRunnable receiver, ConstraintProxy ×4, RescheduleReceiver) | services/receivers | one is `true`: `SystemJobService` | `android.permission.BIND_JOB_SERVICE` |

`RescheduleReceiver` is explicitly `android:enabled="false"` in the merged manifest.

**All exported components are either the launcher activity or protected by a signature-level system
permission.** No unprotected exported component was found. This is the correct configuration.

---

## 7. Contents

161 ZIP entries total.

**DEX:**

| File | Size |
|---|---|
| `classes.dex` | 13,565,616 bytes |
| `classes2.dex` | 7,910,728 bytes |

Two DEX files, ~21.5 MB of bytecode, unminified (`isMinifyEnabled = false`). Expected for a Compose app with
an unshrunk dependency graph; not obfuscation. There is **no** `assets/*.dex`, no secondary payload DEX, and
no encrypted blob that could be loaded at runtime.

**Native libraries (only these three, only for the two ARM ABIs):**

| Path | Size | Origin |
|---|---|---|
| `lib/arm64-v8a/libvosk.so` | 10,042,800 | Vosk offline speech recognition (Apache-2.0) |
| `lib/arm64-v8a/libjnidispatch.so` | 176,520 | JNA (Vosk's bridge) |
| `lib/arm64-v8a/libandroidx.graphics.path.so` | 10,096 | AndroidX graphics path |
| `lib/armeabi-v7a/libvosk.so` | 8,985,628 | Vosk |
| `lib/armeabi-v7a/libjnidispatch.so` | 126,496 | JNA |
| `lib/armeabi-v7a/libandroidx.graphics.path.so` | 7,252 | AndroidX |

**No unexpected native binaries.** No `libunity.so` — the Unity module exists in the repo
(`unity/PipoWorld`) but was **not** compiled into v1.0, consistent with `settings.gradle.kts` including
`:unityLibrary` only when exported.

**No x86/x86_64 libraries** — the APK cannot install on a standard x86 emulator. This matters for testing
(see `DEVICE_TEST_PROTOCOL.md`): an x86 emulator will fail with `INSTALL_FAILED_NO_MATCHING_ABIS`, which is
a *distribution* limitation, not a security finding.

**No `META-INF/*.RSA` / `*.SF` / `*.MF`** — consistent with v1 signing being disabled.

**Assets:** `assets/stage/**` — the Three.js 3D room (JS, models, textures) rendered in a locked-down WebView.

---

## 8. Hardcoded secret scan

The two DEX files (21,476,344 bytes combined) were scanned as ASCII for credential patterns:

| Pattern | Result |
|---|---|
| Google API key `AIza[0-9A-Za-z_-]{35}` | **0 matches** |
| Groq key `gsk_[0-9A-Za-z]{40,}` | **0 matches** |
| `http://` cleartext endpoints | only XML namespace URIs (`www.w3.org/2000/svg`, `schemas.android.com/apk/res/android`) |

Cross-checked against the generated BuildConfig for the release variant:

```java
public static final String GEMINI_API_KEY = "";
public static final String GROQ_API_KEY = "";
public static final String PIPO_PROXY_TOKEN = "";
public static final String PIPO_PROXY_URL = "";
public static final boolean DEBUG = false;
```

**The distributed release APK contains no API keys.** (The *debug* variant does — see
`STATIC_SECURITY_AUDIT.md` finding F5 — but that is a different artifact and is not what users downloaded.)

---

## 9. Structure summary

The APK is a conventional, unmodified Gradle/AGP release build:

- Standard `AndroidManifest.xml`, `resources.arsc`, `res/`, `assets/`, two `classes*.dex`, six ARM native
  libraries, v2 signature block.
- No packers, no droppers, no encrypted payloads, no reflection-loaded DEX, no native packer stubs.
- Manifest and resources are readable and internally consistent with the source tree at the released commit.

---

## 10. Limitations of this analysis

- `jadx` / `apktool` were not installed, so no full decompilation was performed. DEX-level conclusions rely on
  (a) the binary manifest, (b) DEX string scanning, and (c) source review of the released commit — not on
  reconstructed bytecode.
- No malware-scanning service was queried. The APK was **not** uploaded anywhere (see
  `PLAY_PROTECT_ANALYSIS.md` §6 for the approval-gated plan).
- The local source tree has **uncommitted changes** after the released commit; the rebuild comparison in
  `BUILD_COMPARISON.md` accounts for this.
- Play Protect's detection engine is proprietary and was not reproduced.
