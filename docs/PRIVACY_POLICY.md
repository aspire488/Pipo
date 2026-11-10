# Pipo — Privacy Policy

_Effective: 10 November 2026 · Previous version: 2 October 2026_
_Contact and privacy enquiries: https://github.com/aspire488/Pipo/issues_

Pipo is a small robot who lives on your phone. This policy explains, feature by feature, what the app
does with your information. It is written against the actual code in the repository and the actual
APK published on GitHub Releases — not against intentions.

**The short version.** Pipo's world is stored on your phone. There is no account, no analytics SDK and
no advertising SDK. The only things that leave the phone are the requests listed in the table below,
and each one only happens when you use the feature that needs it. **In the build distributed on GitHub
Releases, online AI is not enabled at all**, so Pipo talks with his own built-in words.

---

## 1. What stays on your phone

Stored in the app's private storage, never uploaded:

- **Pipo's whole life** — his traits, moods, memories, journal, the things he owns, his projects, the
  drawings he "took" (drawn pictures, not your photos), Nib, his trips, games and your settings.
- **A random install ID** — a UUID created on first launch. It is used only as a rate-limiting key if
  online AI is enabled. It is not linked to you, your Google account or your device identifiers, and it
  is not used for analytics.
- **Cached weather** — the last reading, so the window doesn't flicker when you reopen the app.
- **The one photo you show Pipo** — written to a temporary file inside the app's own storage, read once,
  then deleted. It is never added to your gallery.

This data is deleted when you uninstall Pipo, or when you use **Settings → Reset Pipo**. There is no
server-side copy to delete, because there is no server-side copy.

---

## 2. What leaves your phone, and why

| Feature | What is sent | To whom | Stored? |
|---|---|---|---|
| **Online AI chat** (only in builds configured with a proxy — *not* in the GitHub release build) | Your message, the last few lines of the conversation, and a short summary of Pipo's state (mood, recent activity, possessions) | The AI proxy you configured, which forwards to Google (Gemini) or Groq | Not stored by Pipo. The provider processes it under its own terms. |
| **"Pipo, look!"** (same condition) | One photo **you** take or choose, downscaled | Same path as above | Pipo deletes the local copy immediately after reading it. |
| **"Find out…" / "ask Gemini…"** (same condition) | Your question | Same path as above | Not stored by Pipo. |
| **"Ask ChatGPT / Claude / Perplexity…"** | Your question, opened in **your browser** | The service you named, directly | Under that service's terms. Pipo sends nothing itself. |
| **Real weather** (optional, on by default) | Your IP address is sent to estimate your city; then that city's latitude/longitude | `ipwho.is`, then `api.open-meteo.com` | Not stored by Pipo beyond a local cache. |
| **Voice input** (tap the mic) | Your speech | Your phone's speech-recognition service (usually Google). Pipo *requests* on-device recognition, but whether audio is processed locally is decided by that service — it may send audio to its provider. | Under that service's terms. |
| **Pipo Voice** — the optional "Pipo, …" wake word | **Nothing.** Recognition runs inside Pipo with an offline model; audio is processed in memory and dropped | Nobody | Nothing is kept. |
| **Pipo Voice model download** (one time, when you enable the feature) | A plain file request, no personal data | `alphacephei.com` (the model's publisher, Vosk) | Not applicable. |
| **"Play X" / "search X" / "open X"** | Your search text, or an app launch intent | YouTube, Google, or the app you named | Under their terms. |

Pipo does **not** send your contacts, your files, your photo library, your exact location, your
notification text, or the content of other apps. There is no crash-reporting SDK and no analytics SDK
in the APK.

### About the GitHub release build specifically

The APK published at
<https://github.com/aspire488/Pipo/releases/download/v1.0/Pipo.apk> contains **no API keys and no proxy
URL**. `AiEndpoints` therefore reports online AI as unavailable, and every chat reply comes from the
on-device, rule-based brain. The online-AI rows above describe what happens **only** if you build Pipo
yourself with your own proxy (`server/` in the repository). This is deliberate: a key inside an APK can
be extracted, so none is shipped.

---

## 3. Third-party services

Pipo contacts these services, and only for the reasons above:

| Service | Purpose | Their policy |
|---|---|---|
| `ipwho.is` | Approximate city from your IP, for the window weather | <https://ipwho.is/> |
| `api.open-meteo.com` | Weather for that city | <https://open-meteo.com/en/terms> |
| `alphacephei.com` | One-time download of the Vosk English speech model | <https://alphacephei.com/vosk/> |
| Your configured AI proxy, then Google Gemini / Groq | Online AI wording, if you enable it | <https://policies.google.com/privacy> · <https://groq.com/privacy-policy/> |
| Your device's speech-recognition service | Turning your speech into text | Determined by your device vendor |
| YouTube, Google Search, Maps, Spotify, the apps you name | Opening what you asked Pipo to open | Those services' own policies |

Pipo has no business relationship with any of them beyond making these requests.

---

## 4. Permissions, and what each one is for

Every permission below is declared in the manifest. The sensitive ones are **special accesses that you
grant yourself in Android's settings**, and each stays off until you do.

- **Microphone (`RECORD_AUDIO`)** — requested the first time you tap the mic. Also used by the optional
  "Pipo Voice" service, which is **off by default**; having the permission does not switch it on.
- **Notifications (`POST_NOTIFICATIONS`)** — so Pipo can send you an occasional message. You choose how
  often, or turn it off.
- **Notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`)** — optional, granted in Android's
  special-access screen. Lets Pipo notice *that* you received a message or reel. He sees the **app name**
  and derives a **coarse kind** (message, photo, reel, call) from the notification on the phone. The text
  is used only to decide that kind, then discarded. Nothing is stored, logged or transmitted, and Pipo
  never opens, replies to or dismisses a notification.
- **Display over other apps (`SYSTEM_ALERT_WINDOW`)** — optional, Android's own switch. Only so that,
  when you say "Pipo, …" from another app, he can briefly show his face. Without it you get a
  notification to tap instead. The bubble cannot be touched and sees nothing.
- **Device admin** — optional, activated on Android's own screen. It requests exactly one policy:
  lock the screen (like the power button), so "Pipo, lock" works. No accessibility service is used and
  Pipo cannot read your screen, apps or typing.
- **Modify system settings (`WRITE_SETTINGS`) / Do Not Disturb access (`ACCESS_NOTIFICATION_POLICY`)**
  — optional special accesses, used only to do what you ask ("brightness down", "do not disturb on").
- **Set alarms (`SET_ALARM`)** — only when you ask Pipo to set one.
- **Internet / network state** — for the weather, the optional model download, opening links, and
  online AI if configured.
- **`WAKE_LOCK` and `RECEIVE_BOOT_COMPLETED`** — merged in by Android's WorkManager library, which runs
  Pipo's once-an-hour "what happened while you were away" tick. They are not used for anything else.
- **Camera** — Pipo does **not** hold the camera permission. "Pipo, look!" opens **your own camera app**
  and you take the photo. The torch is driven through `CameraManager.setTorchMode`, which needs no
  camera permission.
- **Location, contacts, SMS, call log, storage** — not requested at all.

---

## 5. Voice and microphone

- Tapping the mic is a one-shot recording session that ends when you stop speaking.
- **Pipo Voice** is a separate, opt-in feature. When on, it runs a foreground service with a permanent
  notification you can switch it off from. It listens only while the screen is on, the phone is
  unlocked, Pipo is closed and you are not on a call. It takes no audio focus and makes no sound, so it
  never pauses or lowers your music or video.
- Audio is processed in memory by the on-device model and discarded. Nothing that is not a call to Pipo
  is kept, and no audio is written to storage or sent anywhere by Pipo.

---

## 6. Photos of people

When you show Pipo a photo, his instructions forbid identifying anyone, guessing names or ages, or
commenting on appearance. Please do not show him photos of people who would not want that.

---

## 7. Children

Pipo is intended for users **aged 13 and over**. It is not directed at children and is not enrolled in
Google Play's "Designed for Families" programme.

---

## 8. Data retention and deletion

| Data | Where | How long | How to delete |
|---|---|---|---|
| Pipo's world, settings, memories | App-private storage on your phone | Until you reset or uninstall | **Settings → Reset Pipo**, or uninstall the app |
| Install ID | App-private storage | Same | Same |
| Cached weather | App-private storage | Until refreshed or app cleared | Same |
| The photo shown to Pipo | Temporary app-private file | Deleted immediately after he reads it | Automatic |
| Data sent to an AI provider (only if you enable online AI) | The provider's systems | Governed by that provider's policy | Contact that provider; Pipo keeps no copy |
| Data sent to `ipwho.is` / Open-Meteo | Those services' logs | Governed by their policies | Pipo keeps no copy |

Because Pipo has no account system, there is no "delete my account" request to make. Uninstalling is a
complete deletion of everything Pipo holds.

---

## 9. Security

Pipo's save is written atomically (temporary file, then rename) under a single lock, and a corrupt save
is set aside rather than crashing the app. The published APK is signed with a project-owned release key
and is not debuggable. The app does not download or execute code at runtime.

---

## 10. Your choices

- Leave online AI off and Pipo talks with his own built-in words. Everything else still works.
- Turn off weather, notifications, notification access, display-over-other-apps, or device admin at any
  time in Pipo's Settings or in Android's own settings.
- Reset Pipo, or uninstall, to delete everything.

---

## 11. Changes

If this policy changes, the date at the top changes and the new version is published at the same URL.
The policy is versioned in the repository at `docs/PRIVACY_POLICY.md`, so its history is public.

---

## 12. Contact

Privacy questions and data requests: **<https://github.com/aspire488/Pipo/issues>**

There is no separate privacy email address. The issue tracker is the real, monitored contact channel;
using it avoids inventing an address that nobody reads.
