# Pipo - Launch Intelligence Report

**Generated:** 2026-11-14T10:29:24Z  
**Platforms:** 8 configured, 5 measured, 2 needing attention  
**Method:** public endpoints only; no app analytics, no install telemetry.  
**Snapshot:** `launch_tracker/data/latest.json` (git-ignored)

> Downloads are not installs, stars are not users, and a merge is not a listing. Every number below is a platform counter with a stated source.

## Executive overview

| Field | Value |
|---|---|
| Overall health | attention needed |
| Platforms monitored | 8 |
| Measured successfully | 5 |
| Needing attention | Reddit, Product Hunt |
| Unsupported | XERJ |
| Latest significant event | freebuff adapter: partial -> manual |
| Critical events in this run | 0 |

## Distribution

| Signal | Value | Source | Observed |
|---|---|---|---|
| GitHub APK downloads | 25 | GitHub REST API | 2026-11-14T10:29:17Z |
| Freebuff downloads | 23 | manual observation | 2026-11-14T10:29:21Z |
| Latest GitHub release | v1.0 | GitHub REST API | 2026-11-14T10:29:17Z |
| F-Droid package page | 404 | f-droid.org | 2026-11-14T10:29:20Z |
| F-Droid MR | opened | gitlab.com API | 2026-11-14T10:29:20Z |

Reported downloads across counters: **48** (GitHub 25 + Freebuff 23). This is a non-deduplicated sum of two different platform counters, not a number of people.

## Community traction

| Signal | Value |
|---|---|
| GitHub stars | 1 |
| GitHub forks | 0 |
| GitHub watchers | 0 |
| Open issues | 0 |
| Open pull requests | 10 |
| External issues/PRs | 0 |
| Reddit score (tracked posts) | -- |
| Reddit comments | -- |
| Product Hunt votes | -- |
| Hacker News points | 0 |
| Hacker News comments | 0 |
| XERJ indexed documents | -- |

## Growth

| Metric | Now | 24h | 7d | 30d |
|---|---|---|---|---|
| GitHub APK downloads | 25 | -- | -- | -- |
| GitHub stars | 1 | -- | -- | -- |
| GitHub forks | 0 | -- | -- | -- |
| Reddit score | -- | -- | -- | -- |
| Reddit comments | -- | -- | -- | -- |
| HN points | 0 | -- | -- | -- |

`--` means no snapshot old enough exists yet; it does not mean zero.

## Launch timeline

| When (UTC) | Severity | Platform | Event |
|---|---|---|---|
| 2026-11-14 10:24 | normal | GitHub | GitHub APK downloads reached 25 downloads |
| 2026-11-14 10:29 | normal | Freebuff | freebuff adapter: partial -> manual |
| 2026-11-14 10:29 | low | Freebuff | freebuff started reporting downloads: 23 |
| 2026-11-14 10:29 | low | Freebuff | freebuff started reporting observations: 1 |

## Issues requiring attention

- _GitHub_: traffic unavailable: HTTP 403 (1) (token lacks push access); release asset downloads are not installs
- _F-Droid_: pipeline failed with zero jobs: runner availability, not a lint result
- **Reddit** (BLOCKED by platform): r/fossdroid launch: HTTP 403 (1)
- _Hacker News_: a successful search returning nothing is a measured zero, not missing data
- **Product Hunt** (needs credentials): PRODUCT_HUNT_TOKEN not set; public page probe: HTTP 403 (1) Provide `PRODUCT_HUNT_TOKEN`.
- _Freebuff_: Freebuff has no public API; the value is a dated human observation
- _Public surfaces_: not OK: https://f-droid.org/packages/com.pipo.robot/, https://www.producthunt.com/products/pipo-2
- **XERJ** (unsupported): no public XERJ endpoint exists or is documented; configure base_url to enable
- **F-Droid**: MR open with no reviewer assigned yet.

## Milestones

| Milestone | Target | Now | State |
|---|---|---|---|
| GitHub APK downloads | 25 | 25 | reached 2026-11-14 |
| GitHub APK downloads | 50 | 25 | pending |
| GitHub APK downloads | 100 | 25 | pending |
| GitHub APK downloads | 250 | 25 | pending |
| GitHub APK downloads | 500 | 25 | pending |
| GitHub APK downloads | 1000 | 25 | pending |
| Freebuff downloads | 25 | 23 | pending |
| Freebuff downloads | 50 | 23 | pending |
| Freebuff downloads | 100 | 23 | pending |
| Freebuff downloads | 250 | 23 | pending |
| Freebuff downloads | 500 | 23 | pending |
| Freebuff downloads | 1000 | 23 | pending |
| GitHub stars | 10 | 1 | pending |
| GitHub stars | 25 | 1 | pending |
| GitHub stars | 50 | 1 | pending |
| GitHub stars | 100 | 1 | pending |
| GitHub stars | 250 | 1 | pending |
| GitHub stars | 500 | 1 | pending |
| First external issue | 1 | 0 | pending |
| First external issue or PR | 1 | 0 | pending |
| F-Droid listing live | 1 | 0 | pending |

## Next actions

1. Ask an F-Droid maintainer to run CI upstream for MR https://gitlab.com/fdroid/fdroiddata/-/merge_requests/57356; the fork pipeline never reached a runner.
2. Apply the `New App` label on the F-Droid MR (quick actions only work from comments).
3. GitHub stars are 1/10: one honest post in a FOSS community is the cheapest next step.
4. APK downloads are 25/50; record the count before and 24h after any post.
5. Configure `PRODUCT_HUNT_TOKEN` for Product Hunt.

## Sources

| Platform | Status | Source | Observed |
|---|---|---|---|
| GitHub | measured | https://github.com/aspire488/Pipo, https://api.github.com/repos/aspire488/Pipo | 2026-11-14T10:29:17Z |
| F-Droid | measured | https://gitlab.com/fdroid/fdroiddata/-/merge_requests/57356, https://f-droid.org/packages/com.pipo.robot/ | 2026-11-14T10:29:20Z |
| Reddit | BLOCKED by platform | https://www.reddit.com/r/fossdroid/comments/1xvygzx/app_pipo_an_autonomous_little_robot_living_on/ | 2026-11-14T10:29:20Z |
| Hacker News | measured | https://hn.algolia.com, https://news.ycombinator.com | 2026-11-14T10:29:21Z |
| Product Hunt | needs credentials | https://www.producthunt.com/products/pipo-2, https://api.producthunt.com/v2/api/graphql | 2026-11-14T10:29:21Z |
| Freebuff | manual observation | -- | 2026-11-14T10:29:21Z |
| Public surfaces | partial | https://github.com/aspire488/Pipo, https://github.com/aspire488/Pipo/releases/tag/v1.0, https://aspire488.github.io/Pipo/ | 2026-11-14T10:29:24Z |
| XERJ | unsupported | -- | 2026-11-14T10:29:24Z |

## Measurement definitions

| Metric | Definition |
|---|---|
| all_asset_downloads | Every release asset download on GitHub, including non-APK files. |
| apk_downloads | GitHub release APK asset download counter. Downloads, not installs. |
| comments | Comment count reported by the platform. |
| downloads | Platform-reported Freebuff downloads from a dated human observation. |
| external_contributions | Open issues/PRs by humans other than the owner. |
| external_issues | Open issues by humans other than the owner. |
| forks | GitHub forks. Code-reuse intent, not users. |
| listing_live | 1 when f-droid.org serves the package page (HTTP 200), else 0. |
| metadata_checks_passed | Local structural checks that passed for the submitted metadata. |
| metadata_checks_total | Platform-reported value; see the platform column for its source. |
| mr_comments | Comments on the F-Droid merge request. |
| observations | Platform-reported value; see the platform column for its source. |
| open_issues | Open GitHub issues, excluding pull requests. |
| open_prs | Open GitHub pull requests, including bots. |
| package_page_status | HTTP status of the f-droid.org package page. |
| pipeline_jobs | Jobs created in the latest F-Droid MR pipeline. |
| pipelines | Platform-reported value; see the platform column for its source. |
| points | Sum of Hacker News story points across matched submissions. |
| posts_blocked | Platform-reported value; see the platform column for its source. |
| releases | Published GitHub releases returned by the API. |
| stars | GitHub stargazers. Interest signal, not discovery. |
| submissions | Hacker News stories matched to Pipo by the relevance filter. |
| surfaces_total | Configured public URLs probed. |
| surfaces_up | Configured public URLs answering with HTTP 2xx/3xx. |
| tasks_done | Platform-reported value; see the platform column for its source. |
| tasks_total | Platform-reported value; see the platform column for its source. |
| watchers | GitHub subscribers (not the star alias). |

## Caveats

- Downloads are platform counters: repeat downloads, verification hits and mirrors included.
- A missing value is reported as `--`; it is never coerced to zero.
- GitHub release downloads and Freebuff downloads are different counters and are not deduplicated.
- This report is generated data; the tracker code is committed, the data stays local.
