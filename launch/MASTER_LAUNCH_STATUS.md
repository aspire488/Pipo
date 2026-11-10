# Pipo — master launch status

**Updated:** 2026-11-10 (continuation session)
**Repository:** `github.com/aspire488/Pipo` · **Public release:** `v1.0` · **Published APK SHA-256:** `911b61a523da2b0c91c747fdf87fcbfcd318b6bde458385b3bd69c2c121e3526`

This file is the single source of truth for what is actually done. Anything marked **LIVE** was
verified by fetching it; anything marked **BLOCKED** states the exact human action needed. Nothing in
here is a plan for future work that has not happened — claims are limited to observed state.

---

## 1. Completed, with evidence

| # | Item | Evidence |
|---|---|---|
| 1 | Security investigation, 11 documents | `security/play-protect-investigation/`. Verdict: reputation/sideload-verification outcome, **no malware, no P0**. |
| 2 | Provider keys removed from **all** build types | `app/build.gradle.kts` emits `GEMINI_API_KEY` / `GROQ_API_KEY` as `""`. Committed in `0d29bcc`. |
| 3 | Release signing fails hard instead of falling back to the debug key | `gradle.taskGraph.whenReady` guard; proven both ways (see `FINAL_RELEASE_CHECKLIST.md` §2.3). Committed in `0d29bcc`. |
| 4 | `versionCode` 1 → 2, `versionName` 1.0 → 1.1 | `app/build.gradle.kts`. Not released — see §3. |
| 5 | Unit tests green | 266 tests / 39 classes, 0 failures (`testDebugUnitTest`). |
| 6 | **Privacy policy hosted publicly** | **LIVE**: <https://aspire488.github.io/Pipo/privacy/> → HTTP 200, `<title>Pipo — Privacy Policy</title>`, effective 10 Nov 2026. Source `docs/PRIVACY_POLICY.md`, page `web/privacy/index.html` (tracked, commit `0d29bcc`). |
| 7 | GitHub repository metadata | Verified via `gh repo view`: description, homepage `https://aspire488.github.io/Pipo/`, 10 topics (android, android-app, compose-canvas, indie-game, jetpack-compose, kotlin, mit-license, offline-first, simulation, virtual-pet), public. **Correct, so not re-applied.** |
| 8 | README download path | Prominent "GET PIPO / DOWNLOAD APK" badge → release asset, plus release notes, live 3D world, intro video, SHA-256 verification block, privacy link ×2. |
| 9 | Reddit launch kit | `REDDIT_POSTS.md` (4 distinct drafts), `REDDIT_LAUNCH_PLAN.md`, `REDDIT_FAQ.md`, `POSTING_ORDER.md`. |
| 10 | Fastlane store metadata | `fastlane/metadata/android/en-US/` — title, short + full description, changelog, 512×512 icon. Consumed by Google Play, Samsung and F-Droid alike. |
| 11 | Obtainium compatibility | **Compatible and live**: Obtainium tracks GitHub releases containing an APK asset. `v1.0` publishes `Pipo.apk` (27,351,753 bytes, digest `sha256:911b61a5…3526`). No account or configuration on the Pipo side. |
| 12 | Amazon Appstore eligibility resolved | **Pipo is not eligible as a phone app.** Amazon stopped accepting new Android-device submissions on 2025-02-20 and discontinued the Appstore on Android devices on 2025-08-20. See §3. |

---

## 2. Blocked / incomplete, with the exact owner action

| # | Blocker | Why it cannot be done here | Exact owner action |
|---|---|---|---|
| B1 | **Rotate the Gemini and Groq API keys** | Needs provider console access. The keys were embedded in older *debug* APKs handed to test phones. No new build can embed them. | Google AI Studio → API keys → delete/regenerate; Groq Console → API keys → delete/regenerate. Nothing in the repo changes afterwards. |
| B2 | **v1.0 → v1.1 upgrade validation on hardware** | `adb devices` returns an empty list — no device attached. No test result is claimed. | Install the built `app-release.apk` (arm64) over an existing v1.0 install; confirm the save migrates and Pipo opens with his state intact. |
| B3 | **Publish v1.1** | Deliberately gated on B1 + B2. Also, replacing the v1.0 asset would invalidate the Play Protect appeal evidence. | After B1 and B2: `git tag v1.1` + upload release with the new APK and its SHA-256. |
| B4 | **Reddit posts** | `reddit.com/*/about/rules.json` returns **HTTP 403** from this machine (re-confirmed today), and no authenticated Reddit session or API credential is available. | Log in as the developer account and post the drafts from `REDDIT_POSTS.md` in the order in `POSTING_ORDER.md`. |
| B5 | **r/androiddev** | Sanctioned location is the monthly "Interesting Android Apps" showcase thread, which has not run since July 2026. | Check the sub for an open showcase thread; use the comment draft (§4 of `REDDIT_POSTS.md`) — never a standalone post. |
| B6 | **Samsung Galaxy Store** | Requires a Samsung account + Seller Portal + **commercial seller status**, which itself requires a D-U-N-S number and a bank/PayPal record. Cannot be created on the owner's behalf. | Register at <https://seller.samsungapps.com>, request Commercial Seller Status, then upload `app-release.apk` (or an AAB) with the metadata in `fastlane/`. |
| B7 | **Google Play** | Requires a $25 one-time fee, government-ID verification, and for personal accounts a 12-tester / 14-day closed test. | See §3 row "Google Play" for the full gate. |
| B8 | **F-Droid inclusion** | The upstream metadata F-Droid asks for now exists, but filing the `fdroiddata` merge request needs a GitLab account. | Once B4-style accounts exist: fork `gitlab.com/fdroid/fdroiddata`, add `metadata/com.pipo.robot.yml`, open the MR. |
| B9 | **Product Hunt / Show HN / Indie Hackers / itch.io** | All need an authenticated account owned by the developer. | Post `dist\Pipo-Intro.mp4` + the release link; use the r/SideProject body as the base story. |
| B10 | **Store screenshots** | **No current screenshots exist.** `docs/preview/*.png` show the pre-redesign UI (old HUD, "Suit off (Mk I)", low-poly forest) — using them would misrepresent the build. | With a device attached: capture 4–8 portrait shots (room, Nib, a game, the journal) and drop them in `fastlane/metadata/android/en-US/images/phoneScreenshots/`. |
| B11 | **Play Protect appeal** | Drafted in `PLAY_PROTECT_APPEAL_DRAFT.md` with `[…]` placeholders that only the original dialog capture can fill. | File at <https://support.google.com/googleplay/android-developer/contact/protectappeals>. |

---

## 3. Distribution channels — current requirements (researched 2026-11-10)

| Channel | Publishing cost | Account requirements | Technical requirements | Pipo eligible? | Actual status |
|---|---|---|---|---|---|
| **GitHub Releases** | Free | Already have it | Signed APK | Yes | **LIVE** — `v1.0`, 4 downloads |
| **GitHub Pages** (site + privacy) | Free | — | Static files in `web/` | Yes | **LIVE** — site and privacy page both 200 |
| **Obtainium** | Free, no account | None (user installs Obtainium) | GitHub release with an APK asset; version read from the APK | Yes | **LIVE/available** — nothing to submit |
| **Samsung Galaxy Store** | Free to register | Samsung account → Seller Portal → **commercial seller status** (D-U-N-S + bank/PayPal; 4 business days for review, up to 10 for D-U-N-S) | APK or AAB; 64-bit; app name/icon/description must match the installed app | Yes | **NOT SUBMITTED** — B6 |
| **Google Play** | **$25 one-time** (not free) | Play Console, government-ID identity verification, 18+; personal accounts only: closed test with **≥12 testers opted in 14 consecutive days**, plus Android-device verification through the Play Console app | AAB (`bundleRelease`), data-safety form, content rating, notification-listener declaration | Yes | **NOT SUBMITTED** — B7 |
| **Amazon Appstore** | Free | Amazon developer account | Fire OS APK/AAB — **new Android-device submissions closed 2025-02-20**, Appstore discontinued on Android 2025-08-20 | **No** as a phone app. Only as a Fire-tablet app, which is a product decision (phone-portrait UI on a tablet) | **NOT ELIGIBLE** |
| **F-Droid** | Free | GitLab account for the `fdroiddata` MR | Public source + FOSS licence; **FOSS-only dependencies**; tagged releases; upstream fastlane metadata | **Probably yes** — MIT app, deps are Apache-2.0/LGPL (Vosk, JNA) + AndroidX/Compose/WorkManager/kotlinx. Needs a build from source check by their packagers | **NOT SUBMITTED** — metadata now upstream (B8) |
| **itch.io** | Free (donation optional) | itch.io account | APK upload, page copy | Yes | **NOT SUBMITTED** — B9 |
| **Hacker News (Show HN)** | Free | HN account | A link + a title beginning "Show HN:" | Yes | **NOT SUBMITTED** — B9 |
| **Product Hunt** | Free to launch | Product Hunt account | Gallery images + tagline + link | Yes | **NOT SUBMITTED** — B9 |
| **Indie Hackers** | Free | Account | Post | Yes | **NOT SUBMITTED** — B9 |
| **Reddit** (r/SideProject, r/droidappshowcase, r/developersIndia) | Free | Account older than 24 h, 2+ karma for r/droidappshowcase | Native video upload | Yes, with per-sub rules | **NOT POSTED** — B4/B5 |

### Genuinely free channels for Pipo today

GitHub Releases · GitHub Pages · Obtainium. Those three are live. Free but gated behind an account
the owner must create: Samsung (free), F-Droid (free), itch.io, HN, Product Hunt, Indie Hackers,
Reddit. Not free: Google Play ($25 one-time). Not available: Amazon Appstore for phones.

---

## 4. Videos used by this launch

**No video was created, edited, trimmed, re-encoded, moved or renamed.** Hashes verified before and
after this session; all six files in `dist\` are unchanged.

| Path | Length | SHA-256 (prefix) | Intended use |
|---|---|---|---|
| `dist\Pipo-Intro.mp4` | 26 s | `b86ae9bb09dd7bdb…` | Primary: real app recording. r/SideProject, GitHub |
| `dist\Pipo-Intro-Vertical.mp4` | 26 s | `f437935a274d8a7c…` | r/droidappshowcase (portrait house style) |
| `dist\pipo-39s-bgm.mp4` | 39 s | `adc439ca15362b55…` | Short feed cut with music |
| `dist\pipo-39s.mp4` | 39 s | `fdc869612447b2ef…` | Same picture, no music bed |
| `dist\pipo-promo.mp4` | 98 s | `7f3e27aeba620c76…` | YouTube / release notes |
| `dist\pipo-promo-vertical.mp4` | 98 s | `57ecb6de80f61588…` | Reels / Shorts |

`dist/` is git-ignored, so these can only be used by native upload from this machine. Do **not** post
`docs/preview/walkthrough.mp4` — it shows a superseded UI.

---

## 5. Order of operations still required

1. B1 rotate keys → 2. B2 install v1.1 over v1.0 on hardware → 3. B3 publish v1.1 →
4. B10 capture screenshots → 5. B4/B5 Reddit in the sequence from `POSTING_ORDER.md` →
6. B6/B7 store accounts → 7. B8/B9 remaining free channels.

Only steps 1–3 involve the app itself; the rest are account-owner actions with copy and assets
already prepared.
