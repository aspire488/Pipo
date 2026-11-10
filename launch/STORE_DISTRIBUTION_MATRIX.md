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
