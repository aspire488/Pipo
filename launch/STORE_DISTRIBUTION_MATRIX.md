# Pipo — store and distribution matrix

**Updated:** 2026-11-10. Status vocabulary is fixed: **Published** · **Submitted** ·
**Awaiting approval** · **Ready — needs owner action** · **Ineligible** · **Not worth pursuing**.

| Channel | Status | Cost | Owner action required | Blocker / note |
|---|---|---|---|---|
| **GitHub Releases** | **Published** | Free | — | `v1.0`, `Pipo.apk`, SHA-256 `911b61a5…3526`, 4 downloads |
| **GitHub Pages** (site + privacy + classic) | **Published** | Free | — | `/`, `/classic/`, `/privacy/` all 200 |
| **Obtainium** | **Published** (no submission exists) | Free | — | Reads the `v1.0` release asset directly |
| **GitHub Discussions** | **Published** | Free | — | [#14](https://github.com/aspire488/Pipo/discussions/14) `Announcements` · [#15](https://github.com/aspire488/Pipo/discussions/15) `General` (install reference) |
| **Reddit — r/SideProject** | **Ready — needs owner action** | Free | Log in, post draft §1, attach `dist\Pipo-Intro.mp4` | Draft final; account trust matters |
| **Reddit — r/droidappshowcase** | **Ready — needs owner action** | Free | Log in, flair **Promo**, template §2, attach `Pipo-Intro-Vertical.mp4` | Needs account >24 h and 2+ karma; 1 app post / 7 days |
| **Reddit — r/developersIndia** | **Ready — needs owner action** | Free | Flair **I Made This**, engineering text §3 | No install CTA — traffic-driving posts are removed |
| **Reddit — r/androiddev** | **Ready — needs owner action** | Free | Comment §4 in an open monthly showcase thread | **No standalone post.** No thread has run since Jul 2026 |
| **Hacker News (Show HN)** | **Ready — needs owner action** | Free | Submit at `news.ycombinator.com/submit` | New accounts are rate-limited; HN has no posting API |
| **Product Hunt** | **Ready — needs owner action** | Free | Create listing; gallery needs real screenshots | Screenshots blocked: existing `docs/preview/*.png` show a superseded UI |
| **Indie Hackers** | **Ready — needs owner action** | Free | Post build-in-public intro | No public API |
| **itch.io** | **Ready — needs owner action** | Free | New project → Downloadable → upload `Pipo.apk` → price "No payments" | Upload package prepared in `launch/distribution/itchio/` (APK hash-verified). Needs account + cover/screenshots |
| **F-Droid** | **Ready — needs owner action** | Free | Create GitLab account → fork `fdroiddata` → MR with `launch/fdroid/com.pipo.robot.yml` | Metadata prepared and eligibility assessed; **not submitted** |
| **Samsung Galaxy Store** | **Ready — needs owner action** | Free | Samsung account → Seller Portal → Commercial Seller Status (**D-U-N-S + bank/PayPal**) | Cannot be fabricated; ~4 business days review |
| **Google Play** | **Not worth pursuing now** | **$25 one-time** | — | Plus ID verification and, for personal accounts, 12 testers × 14 continuous days. Owner declined paid channels |
| **Amazon Appstore** | **Ineligible** | Free | — | New Android-device submissions closed 2025-02-20; Appstore discontinued on Android 2025-08-20. Fire devices only |
| **Play Protect appeal** | **Ready — needs owner action** | Free | File at the Play Protect appeals form | Needs the original dialog text, which was never captured |

## Summary

- **Published: 4** (GitHub Releases, GitHub Pages, Obtainium, GitHub Discussions) — these already
  serve the APK to anyone and now announce it.
- **Submitted / awaiting approval: 0.** No store review is pending anywhere.
- **Ready for an owner click: 10.**
- **Ineligible: 1.** **Not worth pursuing: 1.**

## Hard rules still in force

- The public release stays **v1.0**. Do not upload the `v1.1` APK (`b9179b90…4255`) — it is unpublished
  and unvalidated.
- Never tell anyone to disable Play Protect.
- F-Droid re-signs with its own key, so an F-Droid install cannot upgrade over a GitHub install and
  switching sources loses the save. Disclose this wherever both are offered.
- No paid promotion, no bought upvotes, no fabricated metrics.

---

## Update — 2026-11-10 (Claude session)

| Channel | Status | Exact blocker |
|---|---|---|
| Hacker News | NOT SUBMITTED | Signed out in the Claude-connected Chrome |
| itch.io | NOT SUBMITTED | Cloudflare check plus signed out in the Claude-connected Chrome |
| Indie Hackers | NOT POSTED | Signed out |
| Product Hunt | NOT LISTED | Signed out |
| F-Droid | NOT SUBMITTED | (1) GitLab signed out; (2) `fdroidserver` not installed, so `fdroid lint`/`fdroid build` were not run; (3) see the F-Droid risks in `CLAUDE_EXECUTION_STATE.md` |
| Reddit | NOT POSTED | Claude in Chrome refuses reddit.com; the owner posts manually |

## F-Droid validation — 2026-11-10 ~17:30 IST (fdroidserver 2.4.5, venv `D:\pipo\.tools\fdroidserver-venv`)

| Check | Result |
|---|---|
| `fdroid lint com.pipo.robot` (real fdroiddata categories + anti-features) | **PASS, exit 0** (`D:\pipo\.tools\logs\fdroid-lint-4.log`) |
| `fdroid build` | **Not runnable on Windows**: crashes before cloning (`fdroid-build.log`). Needs Linux/Docker; Docker's data lives on C:, so it was not used |
| Equivalent build: `git archive 73afc91`, fdroidserver `remove_signing_keys()`, metadata `prebuild` sed, `gradlew assembleRelease` | **BUILD SUCCESSFUL**, output `app-release-unsigned.apk` 27,343,561 B, no signature, 0 key-shaped strings (`v1.0-fdroid-style-assembleRelease.log`) |
| Blocker 1, debug-key fallback | **Resolved in metadata**: `prebuild` sed removes the line; v1.0 history untouched |
| Blocker 3, no fastlane at v1.0 | **Resolved in metadata**: inline Summary/Description |
| Blocker 2, Vosk prebuilt `libvosk.so` (4 ABIs) + JNA `.so` | **Open**: needs an F-Droid maintainer ruling (accept the Maven binaries or build from source) |
| MR | **Held** on purpose, and GitLab is signed out |
| Blocker 2 update (Vosk/JNA) | **Resolved by policy and precedent**: trusted-Maven-repo rule, Apache-2.0/LGPL licences, Dicio (live on F-Droid) uses the same libs with no special handling. Evidence and the MR note are in `fdroid/README.md` §7. MR still held per owner; GitLab verified as `aspire488`. |
| itch.io (2026-11-10 19:52) | **LIVE**: <https://aspire488.itch.io/pipo>, external file → v1.0 GitHub APK (hash verified) |
| Product Hunt | **SCHEDULED** 2026-11-11 00:01 PT |
| F-Droid | Fork plus branch `com.pipo.robot` ready (commit 3d9969d0); the owner opens the MR (`fdroid/MR_DESCRIPTION.md`) |
| GitHub README | PR #16 merged as `8c71af4` |
