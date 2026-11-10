# Pipo — publication log

**Last updated:** 2026-11-10
**Rule for this file:** only verified facts. A submission awaiting review is recorded as *pending*, never
as published. No URL appears here that was not fetched or returned by an API.

---

## 1. Live public artefacts (verified)

| Artefact | URL | Verified how | Date |
|---|---|---|---|
| GitHub repository | <https://github.com/aspire488/Pipo> | `gh repo view` — PUBLIC | 2026-11-10 |
| GitHub release `v1.0` | <https://github.com/aspire488/Pipo/releases/tag/v1.0> | `gh release view` | 2026-11-10 |
| APK download | <https://github.com/aspire488/Pipo/releases/download/v1.0/Pipo.apk> | API digest `sha256:911b61a523da2b0c91c747fdf87fcbfcd318b6bde458385b3bd69c2c121e3526`, 27,351,753 bytes, **4 downloads** | 2026-11-10 |
| Live 3D world | <https://aspire488.github.io/Pipo/> | HTTP 200 | 2026-11-10 |
| Classic 2D world | <https://aspire488.github.io/Pipo/classic/> | linked from README | 2026-11-10 |
| **Privacy policy** | <https://aspire488.github.io/Pipo/privacy/> | HTTP 200, `<title>Pipo — Privacy Policy</title>` | 2026-11-10 |
| **GitHub Discussions announcement** | <https://github.com/aspire488/Pipo/discussions/14> | created via GraphQL, category `Announcements`, discussion #14 | 2026-11-10 |
| Instagram reel (pre-existing) | <https://www.instagram.com/reel/DeEN_MExPP8/> | recorded from the earlier session; not re-fetched | — |

GitHub Pages status: `built`, `public: true`, `build_type: workflow`, served from `web/`.

---

## 2. Store submissions

| Store | Cost | Status | Submitted? | Notes |
|---|---|---|---|---|
| Samsung Galaxy Store | Free | **NOT SUBMITTED** | No | Needs Seller Portal + commercial seller status (D-U-N-S, bank/PayPal). Metadata is ready in `fastlane/`. |
| Google Play | $25 one-time | **NOT SUBMITTED** | No | Needs Play Console, ID verification, and (personal accounts) a 12-tester / 14-day closed test before production access. |
| Amazon Appstore | Free | **NOT ELIGIBLE** | No | New Android-device submissions closed 2025-02-20; Appstore discontinued on Android 2025-08-20. Fire devices only. |
| F-Droid | Free | **NOT SUBMITTED** | No | Upstream fastlane metadata now in the repo (their prerequisite #4). The `fdroiddata` merge request needs a GitLab account. |
| itch.io | Free | **NOT SUBMITTED** | No | Needs an itch.io account. |
| Obtainium | Free | **AVAILABLE, nothing to submit** | — | Obtainium resolves the `v1.0` GitHub release and its APK asset directly. |

**No store listing exists anywhere. No submission is pending review.** Nothing was uploaded to any
store in this session.

---

## 3. Community posts

| Community | Status | Published URL |
|---|---|---|
| **GitHub Discussions** | **PUBLISHED** | <https://github.com/aspire488/Pipo/discussions/14> |
| r/SideProject | **NOT POSTED** — draft ready (`REDDIT_POSTS.md` §1) | — |
| r/droidappshowcase | **NOT POSTED** — draft ready (§2) | — |
| r/developersIndia | **NOT POSTED** — draft ready (§3) | — |
| r/androiddev | **NOT POSTED** — comment draft only, requires an open monthly showcase thread (§4) | — |
| Hacker News (Show HN) | **NOT POSTED** | — |
| Product Hunt | **NOT POSTED** | — |
| Indie Hackers | **NOT POSTED** | — |

Why so few: GitHub is the **only** platform authenticated in this environment (`gh`, account
`aspire488`, scopes `repo` + `write:org`), and it is where the one announcement was published. For
every other platform, `reddit.com/r/*/about/rules.json` returns **HTTP 403** from this machine
(re-confirmed 2026-11-10) and no Reddit, HN, Product Hunt, Indie Hackers, itch.io or GitLab session or
API credential exists. Those need the owner's own session or an API token. The drafts are written and
the video files are local and hash-verified; nothing else is missing except an authenticated human.

**No engagement was fabricated. No post was upvoted, boosted or cross-promoted.**

---

## 4. Play Protect appeal

| Item | Status |
|---|---|
| Appeal text | Written — `launch/PLAY_PROTECT_APPEAL_DRAFT.md` |
| Evidence (APK hash, certificate, permissions) | Complete — `security/play-protect-investigation/` |
| Exact original dialog text | **Missing** — cannot be recovered retroactively; `DEVICE_TEST_PROTOCOL.md` captures it in one pass |
| Submitted to Google | **No** — <https://support.google.com/googleplay/android-developer/contact/protectappeals> |

---

## 5. Version state

| | Value |
|---|---|
| Published release | `v1.0` (`versionCode` 1, `versionName` "1.0"), APK SHA-256 `911b61a5…3526` |
| Working tree | `versionCode` 2, `versionName` "1.1"; release APK SHA-256 `b9179b903f989fb9b9aeaab842dc5eb122bc0df8c5f376b84677a70d6fde4255` |
| v1.1 released? | **No.** Gated on key rotation and real-device upgrade validation. |

The `v1.0` APK and release were **not** modified, replaced or re-uploaded.

---

## 6. Download baseline

`Pipo.apk` on `v1.0`: **4 downloads** as of 2026-11-10. Recorded so any effect of publication is
measurable rather than assumed.
