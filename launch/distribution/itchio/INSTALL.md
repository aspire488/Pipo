# Uploading Pipo to itch.io

**Status: prepared, NOT uploaded.** No itch.io account exists in this environment.

## 1. Project settings

| Field | Value |
|---|---|
| Title | `Pipo` |
| Project URL | `pipo` (→ `pipo.itch.io/pipo`) — check availability, do not assume |
| Classification | **Game** |
| Kind of project | **Downloadable** |
| Pricing | **No payments** (free) |
| Platform | **Android** (tick it; leave Windows/macOS/Linux/web unticked) |
| Release status | Released |
| Tags | see `tags.txt` |
| Community | leave defaults |

Upload `Pipo.apk` as the single downloadable file. Tick **"This file will be played in the browser"**
off — this is a sideloaded APK.

## 2. Description

Paste `description.md`.

## 3. Cover image and screenshots

- **Cover image:** required. Use `fastlane/metadata/android/en-US/images/icon.png` (512×512) or
  `web/og.png` (1200×630) — both are generated from Pipo's own vector icon, not new art.
- **Screenshots (real S23 captures, 2026-11-10):** upload from `launch/store-assets/screenshots/`,
  in order: `01`, `02`, `04`, `05`, `06`, `07`, `08`, `09`. **Never `03`**: it shows the owner's
  first name. `docs/preview/*.png` show a superseded UI and must not be used.

## 4. Paste this into the page (installation + troubleshooting)

```markdown
## How to install

Pipo is an Android app (Android 8.0+, arm64/armv7). It is not on the Play Store — this is the same
signed APK as the GitHub release.

1. Download `Pipo.apk` below.
2. Open it on your phone. If your browser asks, allow "Install unknown apps".
3. Play Protect will say the app is unverified and ask you to confirm. Tap **Install anyway**
   (sometimes under "More details").

**Do not turn Play Protect off.** The warning is expected for any app distributed outside Play, and it
is a verification warning, not a malware detection.

## If something goes wrong

- **"App not installed" / invalid package** — usually a truncated download. Re-download and retry.
- **"Can't install over an existing Pipo"** — a build signed with a different key is installed.
  Uninstalling it **deletes his save**, which lives only on your phone.
- **Nothing happens when I open the APK** — Settings → Apps → (your browser or Files) →
  Install unknown apps.
- **He is silent** — Pipo uses your phone's text-to-speech engine; check
  Settings → Accessibility → Text-to-speech output.
- **No weather in the window** — he needs one network request to estimate your city from your IP.
  No account, no location permission, and it can be turned off in his Settings.

Privacy: https://aspire488.github.io/Pipo/privacy/ · Source: https://github.com/aspire488/Pipo
```

## 5. After publishing

Record the page URL in `launch/PUBLICATION_LOG.md` and `launch/USER_ACQUISITION.md`. itch.io shows view
counts on the project page; it does not report downloads to the developer, so the GitHub APK download
delta remains the real conversion signal.
