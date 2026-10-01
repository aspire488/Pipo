# Play Store release checklist for Pipo

## Must do before the first upload

1. **Deploy the AI proxy** (`server/README.md`) and set `PIPO_PROXY_URL` + `PIPO_PROXY_TOKEN` in
   `local.properties`. Then check that the release APK has no keys:
   `unzip -p app-release.apk classes*.dex | strings | grep -c AIza` should print `0`.
2. **Rotate the Gemini and Groq keys.** The current ones have been compiled into debug APKs on test phones.
3. **Real signing key.** Release currently uses the debug key (`build.gradle.kts`). Create an upload
   key, configure `signingConfigs.release`, and enrol in Play App Signing.
4. **Build an App Bundle**: `./gradlew bundleRelease`.
5. **Host the privacy policy** (`docs/PRIVACY_POLICY.md`, after filling in the date, email and
   audience) at a public URL, and link it in Play Console and in the app's Settings.
6. **Decide the target audience.** If under-13s are a target, the Families Policy applies (AI chat
   and photo upload need extra review and disclosures). The simplest path is 13+.
7. **Content rating questionnaire**: the app has user-generated chat with AI, shares location (city-level, through
   weather), and has no ads or purchases.

## Data safety form (suggested answers)

| Data type | Collected? | Shared? | Purpose | Optional? |
|---|---|---|---|---|
| Messages (in-app chat with Pipo) | Yes, sent to the AI provider through the proxy | Yes, Google and Groq (processing) | App functionality | Yes (offline mode works) |
| Photos | Yes, only one you choose for "Pipo, look!" | Yes, Google and Groq (processing) | App functionality | Yes |
| Audio (voice) | Processed by the device's speech service | Not by the app | App functionality | Yes |
| Approximate location | Yes, city from IP for weather | Yes, ipwho.is and Open-Meteo | App functionality | [add a Settings toggle before launch: see below] |
| App activity / device IDs | Random install ID for rate limiting | No | Fraud prevention / security | No |

- Data is **encrypted in transit** (HTTPS everywhere).
- Users **can request deletion**: everything is on-device (reset/uninstall). The proxy stores nothing.

## Sensitive permission declarations

- **Notification listener**: needs a declaration in Play Console explaining the feature (Pipo
  reacts to "you got a message" moments). It's optional and off by default. Show a prominent
  in-app disclosure before sending the user to the settings page.
- **WRITE_SETTINGS / ACCESS_NOTIFICATION_POLICY**: only used on request. Be ready to justify them,
  or drop these features to keep review simple.

## Nice to have

- A Settings toggle "Use real weather (shares your approximate city)".
- A Settings toggle "AI voice" (off = fully offline Pipo).
- Store listing: screenshots of the room, Nib, the games, the map; a 30-second video.
