# Permission Audit — Pipo

**Artifact:** `Pipo.apk` v1.0 (`com.pipo.robot`), SHA-256 `911b61a5…3526`.
**Source of truth:** the *merged* manifest inside the shipped APK (not the source manifest), because
dependency manifests add permissions.

---

## 1. Summary

| Metric | Value |
|---|---|
| Permissions in shipped APK | **13** |
| Declared in source `AndroidManifest.xml` | 10 |
| Added by dependencies | 3 (`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) |
| Dangerous (runtime-prompted) | 2 (`RECORD_AUDIO`, `POST_NOTIFICATIONS`) |
| Special access (user toggle in Settings) | 3 (`SYSTEM_ALERT_WINDOW`, `WRITE_SETTINGS`, `ACCESS_NOTIFICATION_POLICY`) |
| Clearly unnecessary | **0** |
| Over-broad or unjustified | **0** |
| Not requested at all | location, contacts, SMS, call log, camera, storage, phone state, usage stats, accessibility |

The permission set is **narrow and defensible for the advertised feature set**. There is no permission here
that exists without a corresponding, reachable, user-initiated feature.

---

## 2. Permission-by-permission

### 2.1 `RECORD_AUDIO` — dangerous, runtime
- **Used for:** voice conversation ("Pipo, look!", chat by voice) and the opt-in `PipoVoiceService` wake word.
- **Evidence:** `voice/PipoVoiceService.kt` (`AudioRecord`, `MediaRecorder.AudioSource.VOICE_RECOGNITION`),
  runtime request on mic tap per `DEVICE_TEST_REPORT.md`.
- **Necessary:** yes, for a voice-first character app.
- **Behaviour:** audio is consumed in memory by Vosk and discarded; nothing is written to disk or transmitted
  (`PipoVoiceService.kt` header contract, `WakeModel.kt` "Nothing is ever uploaded").
- **Heuristic relevance:** microphone + a notification listener is a high-signal combination in Android
  malware heuristics. It is legitimate here but is a **plausible contributor** to a behavioural flag.
- **Verdict:** justified. Not removable without deleting the product's core.

### 2.2 `POST_NOTIFICATIONS` — dangerous, runtime (API 33+)
- **Used for:** Pipo occasionally messaging the user about his own life.
- **Evidence:** `notify/Notifier.kt`, `notify/PipoWorker.kt`.
- **Necessary:** yes, for the notification feature. Requested at a sensible moment, not at first launch.
- **Verdict:** justified.

### 2.3 `FOREGROUND_SERVICE` + 2.4 `FOREGROUND_SERVICE_MICROPHONE` — normal
- **Used for:** the opt-in wake-word listener, which must run as a `microphone`-type foreground service.
- **Evidence:** manifest `PipoVoiceService` with `android:foregroundServiceType="microphone"`;
  `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_MICROPHONE)`.
- **Necessary:** **both are mandatory** — Android 14+ rejects a mic foreground service without the typed
  permission.
- **Verdict:** justified, and correctly paired with a persistent, dismissible notification.

### 2.5 `SYSTEM_ALERT_WINDOW` — special app-op
- **Used for:** the optional "pop up when called" face bubble so Pipo can appear over another app when the
  user says "Pipo, …".
- **Evidence:** `voice/PipoPopUp.kt` usage from `PipoVoiceService.bringUp()`.
- **Necessary:** only for that optional behaviour. Off unless the user grants "Display over other apps".
- **Heuristic relevance:** **high.** Overlay permission is a classic banking-trojan / click-fraud signal.
  Combined with a notification listener it is one of the strongest static heuristics on Android.
- **Verdict:** justified but optional. This is the **first candidate** if a heuristic false positive needs to
  be reduced (see `REMEDIATION_PLAN.md` R3).

### 2.6 `INTERNET` — normal
- **Used for:** optional AI chat, weather, wake-model download.
- **Verdict:** justified. Note the app is fully usable offline (offline brain).

### 2.7 `ACCESS_NETWORK_STATE` — normal
- **Used for:** "is the internet there?" checks before network calls.
- **Verdict:** justified, normal, auto-granted, no prompt.

### 2.8 `com.android.alarm.permission.SET_ALARM` — normal
- **Used for:** "set an alarm for 7am" via the system Clock app.
- **Verdict:** justified; this is the documented, user-visible intent-based alarm API. Pipo asks for
  confirmation before creating one (`DEVICE_TEST_REPORT.md`).

### 2.9 `WRITE_SETTINGS` — special app-op
- **Used for:** screen brightness and auto-rotate on explicit request.
- **Evidence:** `phone/Phone.kt` — `Settings.System.putInt(...)` guarded by `Settings.System.canWrite(ctx)`;
  the app deep-links to `ACTION_MANAGE_WRITE_SETTINGS` only when the user asks for the feature.
- **Necessary:** only for those two commands.
- **Heuristic relevance:** moderate — a modify-system-settings capability that malware sometimes abuses.
- **Verdict:** justified but optional. Candidate for reduction if the feature is expendable.

### 2.10 `ACCESS_NOTIFICATION_POLICY` — normal (DND access is a user toggle)
- **Used for:** "silent mode" / Do Not Disturb on request.
- **Evidence:** `phone/Phone.kt` — `isNotificationPolicyAccessGranted` checked before any change.
- **Verdict:** justified but optional. Lowest-value of the special accesses.

### 2.11 `WAKE_LOCK` — normal, **merged**
- **Source:** `androidx.work` (WorkManager), not the app manifest.
- **Used for:** the hourly `PipoWorker` periodic job.
- **Verdict:** expected side effect of WorkManager; not removable without dropping the worker.

### 2.12 `RECEIVE_BOOT_COMPLETED` — normal, **merged**
- **Source:** `androidx.work` (`RescheduleReceiver`).
- **Used for:** re-arming periodic work after reboot.
- **Note:** the merged `RescheduleReceiver` is `android:enabled="false"`; WorkManager uses JobScheduler on
  API 23+, so this is inert in practice.
- **Verdict:** expected. It does **not** indicate an app-authored boot receiver — there is no `BOOT_COMPLETED`
  intent-filter owned by Pipo's own code.

### 2.13 `com.pipo.robot.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — signature, **merged**
- **Source:** AGP, for `Context.registerReceiver(..., RECEIVER_NOT_EXPORTED)` compatibility on API 33+.
- **Verdict:** standard, harmless, app-private.

---

## 3. Permission combinations that matter for heuristics

Individually every permission is justified. The **combination** is what a static classifier reacts to:

```
RECORD_AUDIO  +  FOREGROUND_SERVICE_MICROPHONE
              +  NotificationListenerService (BIND_NOTIFICATION_LISTENER_SERVICE)
              +  DeviceAdmin (BIND_DEVICE_ADMIN)
              +  SYSTEM_ALERT_WINDOW
              +  WRITE_SETTINGS
```

That set — microphone capture, notification reading, overlay, device admin, settings modification — is
essentially the **canonical Android spyware / banking-trojan permission profile**. Real malware uses it to
read 2FA codes from notifications, draw phishing overlays, and lock the device for ransom.

Here, each capability is:

- **opt-in** and off by default (voice, notification access, overlay, device admin, DND, write-settings);
- **narrow** (device admin declares *only* `force-lock`; the listener keeps *only* app + coarse kind);
- **gated** by Android's own consent screens, which the user must pass through;
- **documented** in-app in Settings.

So the combination is **not evidence of malice** — but it is a strong, honest explanation for why an
automated system might treat this APK as high-risk until it has behavioural evidence to the contrary.

---

## 4. What is *not* requested (positive evidence)

Verified absent from the merged manifest:

- Location (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`) — weather is IP-based, via `ipwho.is`.
- Contacts, SMS, call log, phone state.
- `CAMERA` — the torch uses `CameraManager.setTorchMode`, which needs no permission; photos are taken by the
  *user's* camera app via intent.
- Storage / `MANAGE_EXTERNAL_STORAGE` / `READ_MEDIA_*`.
- `QUERY_ALL_PACKAGES` — package visibility is scoped with an explicit `<queries>` block.
- `REQUEST_INSTALL_PACKAGES` — **the app cannot install other APKs.**
- `BIND_ACCESSIBILITY_SERVICE` — **no accessibility service exists.** This is important: accessibility abuse
  is the most common Android malware technique, and Pipo does not use it at all.
- `PACKAGE_USAGE_STATS`, `READ_PHONE_NUMBERS`, `PROCESS_OUTGOING_CALLS`, `SYSTEM_ALERT_WINDOW` beyond the
  declared feature.

---

## 5. Verdict

- **Are any permissions unnecessary?** **No.** Each maps to a real, reachable feature.
- **Are any over-broad?** **No.** No wildcard package visibility, no storage, no location, no SMS.
- **Could permissions contribute to a Play Protect heuristic?** **Yes, plausibly** — via the *combination*
  in §3, not via any single over-broad request. This is a P3-class (heuristic/reputation) contributor, not a
  P1 vulnerability.
- **Confidence:** high for the inventory (extracted from the shipped binary), medium for the weighting any
  external classifier assigns to it (proprietary and unobservable).
