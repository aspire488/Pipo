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
| **GitHub Discussions install reference** | <https://github.com/aspire488/Pipo/discussions/15> | category `General`, discussion #15 — requirements, Play Protect, known limitations | 2026-11-10 |
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
| **GitHub Discussions** | **PUBLISHED** (2 posts) | [#14 Announcements](https://github.com/aspire488/Pipo/discussions/14) · [#15 General](https://github.com/aspire488/Pipo/discussions/15) |
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

---

## 2026-11-10 — Claude session (publishing attempt 2)

**Nothing was published in this attempt.** The owner reported all five platforms as signed in, but in
the only Chrome connected to Claude in Chrome ("Browser 1", Windows, this PC) each one was still
signed out:

| Platform | Observed state (verified in Chrome) | Submitted? |
|---|---|---|
| Hacker News | `/submit` → "You have to be logged in to submit." | No. No duplicate risk |
| itch.io | `/dashboard` stuck on Cloudflare "Performing security verification" | No |
| Indie Hackers | Header shows "Sign in" / "Join" | No |
| Product Hunt | Header shows "Sign in" button | No |
| GitLab | `/dashboard/projects` → redirect to `/users/sign_in` | No |
| Reddit | Refused by Claude in Chrome: "This site is not allowed due to safety restrictions" | No (owner posts manually) |

Pre-publication check done: the public `dist/Pipo.apk` (SHA-256 `911b61a5…3526`, identical to the
release asset) contains **0** Google-key-shaped and **0** Groq-key-shaped strings, so it is safe to
mirror on itch.io. The S23's installed debug build does contain 2 Groq-key-shaped strings and must
never be distributed.

Real S23 screenshots for listings: `launch/store-assets/screenshots/` (8 public files; the name-bearing capture is deliberately excluded, see its README).

## 2026-11-10 ~17:20 IST — publishing attempt 3

Account check, done directly in the Claude-controlled Chrome (`Browser 1`, the only one connected),
twice (~17:15 and ~17:22), on each site's own home or dashboard page:

| Platform | What the page showed | Result |
|---|---|---|
| Hacker News | top bar `login` link, no username | **BLOCKED: signed out.** Not submitted |
| itch.io | `Log in` / `Register` links | **BLOCKED: signed out.** No page created |
| Indie Hackers | `Sign in` / `Join` links | **BLOCKED: signed out.** Not posted |
| Product Hunt | `Sign in` button | **BLOCKED: signed out.** No listing |
| GitLab | `/dashboard/projects` redirects to the sign-in form | **BLOCKED: signed out.** No MR (also deliberately held, see F-Droid) |

Nothing was submitted anywhere, so there is no risk of a duplicate submission. Next action: the owner signs in inside the
Chrome window that shows Claude's tab group (the extension's own window), then says "go".

## 2026-11-10 — attempt 4: auth handoff fixed

Root cause of the "signed out" loop: the owner's sessions were in a **second Chrome profile** that
connected to Claude in Chrome later. Claude had been driving a different profile, which had no HN
cookie at all (checked with `document.cookie`). The session was switched to the signed-in profile.

<p><sub>Personal account handles, email addresses, device identifiers and private editor URLs are deliberately not recorded in this public log.</sub></p>

| Platform | Account verified how | Result |
|---|---|---|
| Hacker News | Top bar showed the owner's account and a logout link | **REJECTED by HN policy**: submitting redirected to `/showlim`, "We're temporarily restricting Show HNs … mostly by users who aren't yet familiar with the site". The account was created ~21 min earlier, karma 1. The account's submissions page shows **0 items**, so nothing was posted. Not resubmitted, and not reposted as a non-Show HN link. Next: the owner builds a normal comment history on HN, then posts one Show HN. |
| itch.io | Creator Dashboard user menu = `aspire488` | **BLOCKED: email not verified.** Draft project created (slug `pipo`, **Draft**, not public; editor URL held locally). Saved: title, tagline, full description with install and troubleshooting text, SHA-256, links, genre Simulation, 8 tags, No payments, AI disclosure **Yes** (Graphics + Text & Dialog + Code, not Sounds) with the owner's statement. The file upload fails server-side with "Please verify your email address before uploading a file". Next: the owner verifies the itch.io account email. Then attach `Pipo.apk` (external link to the v1.0 release asset, or a direct upload), screenshots 01/02/04–09 (not 03), and a cover. Then set Public. |
| Indie Hackers | Header `Account menu for aspire488` | **BLOCKED by platform policy**: `/new-post` says "You can't post just yet. Publishing is open to established members. Join in with a few thoughtful comments … or subscribe to IH+". IH+ is paid, so not used. Profile has no posts; nothing published. Next: the owner leaves a few genuine comments, then posts. |
| Product Hunt | User menu showed the owner's account | **DRAFT READY, NOT LAUNCHED (needs a date from the owner)**. Draft editor URL held locally. All 7 required items done (PH: "Your page is ready to schedule"): name, tagline "A little robot who lives his own life on your Android phone", description, logo (fastlane icon), gallery (site OG card + screenshots 01, 02, 04, 05, 06; not 03), launch tags Android / Free Games / Indie Games, role "I made this". Also pricing Free, links to the repo and the v1.0 release, and a maker first comment. PH has no instant launch: every launch must be **scheduled** (Nov 11–Dec 11 offered; goes live at 00:00 PT for the day). No date chosen. Next: the owner picks a day they can answer comments, then clicks Schedule launch. |
| GitLab | `gon.current_username` = `aspire488` on /dashboard/projects | **AUTHENTICATED. MR intentionally NOT opened** (owner instruction). F-Droid lint passes; Vosk/JNA verdict in `fdroid/README.md` §7. |

**Live public URLs created in attempt 4: none.** Two drafts exist (itch.io, Product Hunt), neither public.

## 2026-11-10 19:52 IST — itch.io PUBLISHED

| Platform | Result | Verified URL | Verification |
|---|---|---|---|
| **itch.io** | **PUBLISHED (Public)** | <https://aspire488.itch.io/pipo> | Logged-out curl: HTTP 200, title "Pipo by aspire488", 6 screenshots (2160-px copies of 01/02/04–09; 03 never uploaded), cover = `web/og.png`, file "Pipo v1.0 (Android APK)" (external, Android). Simulated download: POST `/pipo/file/20417991` → `{"url":"https://github.com/aspire488/Pipo/releases/download/v1.0/Pipo.apk","external":true}` → final 200 from release-assets.githubusercontent.com, **SHA-256 911b61a5…3526, 27,351,753 B = public v1.0**. AI disclosure: Graphics + Text & Dialog + Code (owner statement). No payments. |

Note: that verification download added **1** to the GitHub v1.0 download counter (Claude's test, not a user).
Screenshot derivatives for itch.io: `launch/store-assets/screenshots/itchio-2160h/` (996×2160, lanczos). Originals untouched.
| **Product Hunt** | **SCHEDULED** for Wed 2026-11-11 00:01 PT (13:31 IST), 24 h on the homepage. PH confirmed "Successfully Scheduled!". Draft editor URL held locally. The public product URL appears at launch. PH rules shown: no upvote services or groups; share genuinely. |
| GitHub README | **MERGED** as commit `8c71af4` (<https://github.com/aspire488/Pipo/pull/16>): 4 real S23 screenshots plus the store set (03 excluded, name redacted). |
| F-Droid | **BRANCH READY, MR NOT OPENED**: fork <https://gitlab.com/aspire488/fdroiddata> (public, default branch only), branch `com.pipo.robot`, commit `3d9969d0` "New app: Pipo" adds `metadata/com.pipo.robot.yml` (byte-identical to the linted file, sha `b8882a1b…`, LF). No existing Pipo MR upstream. Opening the MR via the API was refused by the session permission policy ("External System Writes"). The owner opens it: link and pasteable description in `fdroid/MR_DESCRIPTION.md`. |

**2026-11-10 20:13 IST summary:** itch.io LIVE · Product Hunt SCHEDULED 11 Nov 00:01 PT · README PR #16 merged · F-Droid branch ready, MR is the owner's click · HN and IH blocked by new-account rules.
